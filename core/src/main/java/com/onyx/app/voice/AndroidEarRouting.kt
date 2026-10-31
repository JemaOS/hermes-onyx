package com.onyx.app.voice

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/**
 * Le capteur de proximité du téléphone, branché sur [ProximityEarRouter].
 *
 * `SensorManager` n'existe pas dans un test JVM : c'est pourquoi le routeur prend un
 * [ProximityWatcher]. Ce fichier est la seule partie Android du portage « à l'oreille » ; il ne
 * contient aucune décision, seulement la plomberie.
 */
class SensorProximityWatcher(
    context: Context,
    /** Vrai quand quelque chose est tout près du capteur (généralement : une tête). */
    private val onNear: (Boolean) -> Unit,
) : ProximityWatcher {
    private val sensors = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    override val isAvailable: Boolean get() = sensors != null && sensor != null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            // Le capteur de proximité rend une distance : plus petite que sa portée = tout près.
            onNear(event.values.firstOrNull()?.let { it < event.sensor.maximumRange } ?: false)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    override fun start() {
        if (!isAvailable) return
        runCatching {
            sensors?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun stop() {
        runCatching { sensors?.unregisterListener(listener) }
    }
}

/**
 * Vrai quand le téléphone est posé **face contre la table** : l'accéléromètre lit alors la
 * pesanteur sur -Z. Le capteur de proximité se déclenche aussi dans cette position, et ce n'est
 * pas une oreille (la correction de la référence, `AudioSession:233-241`).
 */
class AccelerometerFaceDownDetector(context: Context) : FaceDownSensing {
    private val sensors = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /** Dernière lecture : l'axe Z de la pesanteur, en m/s², signe vers le bas de l'écran. */
    @Volatile
    private var z: Float = 0f

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.values.size >= 3) z = event.values[2]
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /** Vrai si l'écran regarde le sol : la pesanteur pousse sur -Z. */
    override fun isFaceDown(): Boolean = z < FACE_DOWN_Z

    override fun start() {
        if (sensor == null) return
        runCatching { sensors?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    override fun stop() {
        runCatching { sensors?.unregisterListener(listener) }
    }

    private companion object {
        /** À plat, écran en bas, la pesanteur lit ≈ -9,8 m/s² ; le seuil laisse passer l'inclinaison. */
        const val FACE_DOWN_Z = -7.0f
    }
}

/**
 * La sortie audio, appliquée par `AudioManager` : l'écouteur comme un appel, ou le haut-parleur.
 *
 * C'est le pendant Android de `AVAudioSession.overrideOutputAudioPort`. Un casque filaire, un
 * Bluetooth ou un appareil USB/auditif sont laissés tranquilles : Onyx ne se bat pas avec eux —
 * et Android Auto, lui, joue dans la voiture, jamais sur ce téléphone.
 */
class AudioManagerEarRoute(context: Context) : EarRouteController {
    private val audio = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val main = Handler(Looper.getMainLooper())

    /** La dernière sortie demandée : réappliquée si la route change sous nos pieds. */
    @Volatile
    private var current: EarOutput = EarOutput.SPEAKER

    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = reapply()

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = reapply()
    }

    init {
        runCatching { audio?.registerAudioDeviceCallback(devices, main) }
    }

    /** Vrai quand un casque, un Bluetooth, un USB ou un appareil auditif porte déjà le son. */
    override fun hasExternalRoute(): Boolean {
        val manager = audio ?: return false
        return runCatching {
            manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in EXTERNAL }
        }.getOrDefault(false)
    }

    override fun apply(output: EarOutput) {
        current = output
        val manager = audio ?: return
        runCatching {
            when (output) {
                EarOutput.EARPIECE -> {
                    manager.mode = AudioManager.MODE_IN_COMMUNICATION
                    @Suppress("DEPRECATION")
                    manager.isSpeakerphoneOn = false
                }

                EarOutput.SPEAKER -> {
                    manager.mode = AudioManager.MODE_IN_COMMUNICATION
                    @Suppress("DEPRECATION")
                    manager.isSpeakerphoneOn = true
                }

                EarOutput.LEAVE_ALONE -> {
                    // Rien à imposer : on rend le mode au téléphone et on laisse la route choisie.
                    manager.mode = AudioManager.MODE_NORMAL
                }
            }
        }
    }

    /** Rendre la main : plus de mode imposé, la sortie redevient celle du téléphone. */
    override fun restore() {
        current = EarOutput.SPEAKER
        runCatching {
            @Suppress("DEPRECATION")
            audio?.isSpeakerphoneOn = true
            audio?.mode = AudioManager.MODE_NORMAL
        }
    }

    /** L'écran vocal se ferme : plus d'écoute des changements d'appareil non plus. */
    override fun release() {
        restore()
        runCatching { audio?.unregisterAudioDeviceCallback(devices) }
    }

    /** La sortie audio a changé (casque branché, Bluetooth qui négocie) : on remet la nôtre. */
    private fun reapply() = apply(current)

    private companion object {
        val EXTERNAL = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_HEARING_AID,
        )
    }
}
