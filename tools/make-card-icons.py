#!/usr/bin/env python3
"""Écrit les vector drawables des cartes de réponse (météo, carte) dans core/src/main/res/drawable.

Les SF Symbols de la référence n'existent pas sur Android : chaque symbole employé par les cartes
(`ReplyCardViews`) a ici son équivalent, dessiné en blanc (les vues le teintent, comme `OnyxIcon`).
Les formes sont composées de cercles, de rectangles et de traits — assez pour se lire à 20 dp.
"""

from pathlib import Path

OUT = Path("core/src/main/res/drawable")

HEAD = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
"""
TAIL = "</vector>\n"
WHITE = "#FFFFFFFF"


def circle(cx, cy, r):
    return (f'    <path android:pathData="M{cx - r},{cy} a{r},{r} 0 1 0 {2 * r},0 a{r},{r} 0 1 0 {-2 * r},0 Z" '
            f'android:fillColor="{WHITE}" />\n')


def rect(x, y, w, h):
    return f'    <path android:pathData="M{x},{y} H{x + w} V{y + h} H{x} Z" android:fillColor="{WHITE}" />\n'


def fill(path):
    return f'    <path android:pathData="{path}" android:fillColor="{WHITE}" />\n'


def stroke(path, width=1.9):
    return (f'    <path android:pathData="{path}"\n'
            f'        android:strokeColor="{WHITE}"\n'
            f'        android:strokeWidth="{width}"\n'
            '        android:strokeLineCap="round"\n'
            '        android:strokeLineJoin="round"\n'
            f'        android:fillColor="@android:color/transparent" />\n')


def ring(cx, cy, r, width=2.0):
    return stroke(f"M{cx - r},{cy} a{r},{r} 0 1 0 {2 * r},0 a{r},{r} 0 1 0 {-2 * r},0 Z", width)


def write(name, body):
    (OUT / f"{name}.xml").write_text(HEAD + body + TAIL)
    print("wrote", name)


def cloud(shift_x=0.0, shift_y=0.0, scale=1.0):
    """Une nue d'un seul tenant : trois cercles et un ventre plat."""
    def point(x, y, r):
        return circle(x * scale + shift_x, y * scale + shift_y, r * scale)

    return (
        point(9, 14.6, 3.4)
        + point(13.8, 13.0, 4.4)
        + point(17.4, 14.9, 2.7)
        + rect(9 * scale + shift_x, 13.8 * scale + shift_y, 8.4 * scale, 4.2 * scale)
    )


def sun(cx=6.9, cy=6.4, r=3.0):
    body = circle(cx, cy, r)
    for dx, dy in ((0, -1), (0, 1), (-1, 0), (1, 0), (-0.71, -0.71), (0.71, -0.71), (-0.71, 0.71), (0.71, 0.71)):
        body += stroke(f"M{round(cx + dx * (r + 0.9), 2)},{round(cy + dy * (r + 0.9), 2)} "
                       f"L{round(cx + dx * (r + 2.4), 2)},{round(cy + dy * (r + 2.4), 2)}", 1.7)
    return body


def crescent(cx=6.6, cy=6.2, r=3.7):
    """Une lune : le disque évidé par un second disque décalé."""
    return (f'    <path android:pathData="M{cx + r * 0.4},{round(cy - r, 2)} A{r},{r} 0 1 1 {cx + r * 0.4},{round(cy + r, 2)} '
            f'A{r * 0.8},{r * 0.8} 0 1 0 {cx + r * 0.4},{round(cy - r, 2)} Z" android:fillColor="{WHITE}" />\n')


# ---- météo
write("ic_sym_cloud_fill", cloud())
write("ic_sym_cloud_sun_fill", cloud(shift_x=1.8, shift_y=2.6, scale=0.9) + sun())
write("ic_sym_cloud_moon_fill", cloud(shift_x=1.8, shift_y=2.6, scale=0.9) + crescent())
write("ic_sym_cloud_fog_fill", cloud(shift_y=-1.2, scale=0.9)
      + stroke("M7.2,19.2 H17.2", 1.8) + stroke("M8.8,21.4 H18.8", 1.6))
write("ic_sym_cloud_drizzle_fill", cloud(shift_y=-1.4, scale=0.9)
      + "".join(stroke(f"M{x},{18.8} L{x - 1.0},{21.2}", 1.8) for x in (8.6, 12.4, 16.2)))
write("ic_sym_cloud_rain_fill", cloud(shift_y=-1.6, scale=0.9)
      + "".join(stroke(f"M{x + 1.5},{18.4} L{x - 0.6},{22.2}", 1.9) for x in (8.0, 12.0, 16.0)))
write("ic_sym_cloud_heavyrain_fill", cloud(shift_y=-2.0, scale=0.86)
      + "".join(stroke(f"M{x + 1.6},{17.8} L{x - 0.8},{22.4}", 2.0) for x in (7.2, 10.6, 14.0, 17.4)))
write("ic_sym_cloud_bolt_rain_fill", cloud(shift_y=-2.0, scale=0.86)
      + stroke("M8.6,18.0 L11.0,20.6", 1.8) + stroke("M15.4,18.0 L17.8,20.6", 1.8)
      + fill("M13.2,16.6 L10.2,21.2 H12.2 L11.0,23.6 L14.6,18.8 H12.6 Z"))
write("ic_sym_cloud_snow_fill", cloud(shift_y=-1.6, scale=0.9)
      + "".join(stroke(f"M{x},{19.0} L{x},{22.0}", 1.6)
                + stroke(f"M{x - 1.2},{19.4} L{x + 1.2},{21.6}", 1.4)
                + stroke(f"M{x + 1.2},{19.4} L{x - 1.2},{21.6}", 1.4) for x in (9.0, 15.0)))
write("ic_sym_cloud_sleet_fill", cloud(shift_y=-1.6, scale=0.9)
      + stroke("M9.0,18.6 L7.4,22.0", 1.8) + circle(12.8, 20.4, 0.95) + circle(16.6, 20.4, 0.95))
write("ic_sym_cloud_hail_fill", cloud(shift_y=-1.6, scale=0.9)
      + "".join(circle(x, 20.3, 1.05) for x in (8.4, 11.8, 15.2, 18.2)))
write("ic_sym_wind", stroke("M3.2,8.6 H14.4 a2.5,2.5 0 1 0 -2.5,-2.5", 2.0)
      + stroke("M3.2,12.8 H18.2 a2.6,2.6 0 1 1 -2.6,2.6", 2.0)
      + stroke("M3.2,17.0 H10.6 a2.3,2.3 0 1 1 -2.3,2.3", 2.0))
write("ic_sym_thermometer_medium", stroke("M10.6,14.0 V5.6 a2.0,2.0 0 1 1 4.0,0 V14.0", 2.0)
      + circle(12.6, 16.6, 3.1)
      + stroke("M15.8,7.2 H17.8", 1.6) + stroke("M15.8,10.4 H18.6", 1.6))

# ---- la carte et ses actions
write("ic_sym_map", stroke("M3.0,6.4 L9.0,4.4 L15.0,6.4 L21.0,4.4 V17.6 L15.0,19.6 L9.0,17.6 L3.0,19.6 Z", 1.9)
      + stroke("M9.0,4.4 V17.6", 1.6) + stroke("M15.0,6.4 V19.6", 1.6))
write("ic_sym_location_fill", fill("M12,2.4 A6.6,6.6 0 0 0 5.4,9 c0,4.9 6.6,12.6 6.6,12.6 S18.6,13.9 18.6,9 A6.6,6.6 0 0 0 12,2.4 Z")
      + fill("M12,6.4 a2.6,2.6 0 1 0 0,5.2 a2.6,2.6 0 1 0 0,-5.2 Z"))
write("ic_sym_flag_fill", stroke("M6.2,21.4 V3.4", 2.0)
      + fill("M7.4,4.4 L18.4,4.4 L15.2,8.6 L18.4,12.8 L7.4,12.8 Z"))
write("ic_sym_arrow_triangle_turn_up_right_diamond_fill",
      fill("M12,3.2 L22.0,12.2 L12,21.2 L2.0,12.2 Z")
      + stroke("M8.4,13.8 V11.2 a1.8,1.8 0 0 1 1.8,-1.8 H15.6", 2.0)
      + fill("M14.4,7.2 L18.6,9.4 L14.4,11.6 Z"))
write("ic_sym_car_fill", fill("M5.0,11.4 L6.8,6.8 A1.8,1.8 0 0 1 8.5,5.6 H15.5 A1.8,1.8 0 0 1 17.2,6.8 L19.0,11.4 "
      "A2.6,2.6 0 0 1 21.0,13.8 V17.4 A1.2,1.2 0 0 1 19.8,18.6 H5.0 A1.2,1.2 0 0 1 2.8,17.4 V13.8 "
      "A2.6,2.6 0 0 1 5.0,11.4 Z")
      + circle(6.9, 19.2, 1.5) + circle(17.1, 19.2, 1.5))
write("ic_sym_figure_walk", circle(13.0, 4.2, 2.1)
      + stroke("M12.4,7.4 L11.0,12.6 L13.6,15.2 L13.0,21.4", 2.1)
      + stroke("M12.2,8.6 L8.4,11.2 L6.6,15.0", 2.1)
      + stroke("M12.6,9.2 L16.4,11.6 L18.4,15.4", 2.1)
      + stroke("M11.0,12.6 L7.6,17.4 L5.6,21.4", 2.1))
write("ic_sym_tram_fill", stroke("M6.4,4.6 H17.6 V16.4 H6.4 Z", 2.0)
      + stroke("M9.0,8.4 H15.0 V11.8 H9.0 Z", 1.6)
      + stroke("M8.4,16.4 L6.4,20.2", 1.9) + stroke("M15.6,16.4 L17.6,20.2", 1.9)
      + stroke("M11.0,3.0 H13.0", 1.6))
write("ic_sym_bicycle", ring(6.0, 16.4, 4.0, 1.9) + ring(18.0, 16.4, 4.0, 1.9)
      + stroke("M6.0,16.4 L10.2,9.6 H14.2 L18.0,16.4", 1.9)
      + stroke("M10.2,9.6 L12.6,16.4 H18.0", 1.9)
      + stroke("M9.4,7.2 H12.2", 1.7) + stroke("M14.0,7.2 H16.4", 1.7))
write("ic_sym_phone_fill", fill("M6.6,2.8 H17.4 A2.2,2.2 0 0 1 19.6,5.0 V19.0 A2.2,2.2 0 0 1 17.4,21.2 H6.6 "
      "A2.2,2.2 0 0 1 4.4,19.0 V5.0 A2.2,2.2 0 0 1 6.6,2.8 Z M9.4,6.2 H14.6 V7.8 H9.4 Z"))
write("ic_sym_safari", ring(12.0, 12.0, 9.2, 1.9) + fill("M16.4,7.6 L13.4,13.4 L7.6,16.4 L10.6,10.6 Z"))

print("done")
