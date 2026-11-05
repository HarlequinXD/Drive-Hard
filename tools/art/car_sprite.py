"""
Hand-drawn pixel-art car: a red 90s Japanese hatchback, 8 directions (5 drawn, 3 mirrored).

Everything is 2D: each view is drawn with pixel shapes placed by hand (polygons, lines, rects,
single pixels) in a fixed palette, then outlined. Game scale is 32 px per metre.

python3 tools/art/car_sprite.py out_dir
"""
import os
import sys
from PIL import Image, ImageDraw

PAL = {
    # body (red)
    'r1': (255, 128, 112), 'r2': (226, 58, 56), 'r3': (186, 34, 40), 'r4': (132, 20, 30), 'r5': (84, 12, 22),
    # glass
    'g1': (196, 222, 240), 'g2': (120, 152, 190), 'g3': (66, 86, 120), 'g4': (34, 42, 62), 'g5': (22, 26, 38),
    # chrome / grey trim
    'c1': (240, 242, 246), 'c2': (182, 186, 196), 'c3': (120, 124, 136), 'c4': (78, 80, 92),
    # black trim, tyres
    'k1': (52, 52, 60), 'k2': (30, 30, 36), 'k3': (16, 16, 20),
    # lights
    'y1': (255, 252, 226), 'y2': (226, 220, 182), 'o1': (255, 168, 48), 'o2': (214, 112, 22),
    'l1': (255, 92, 74), 'l2': (196, 30, 34), 'l3': (120, 16, 22),
    # plate
    'p1': (244, 244, 232), 'p2': (40, 92, 60),
}

W, H = 144, 84
GROUND = 62


class Canvas:
    def __init__(self, ox=0, oy=0):
        self.img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)
        self.ox, self.oy = ox, oy

    def _p(self, pts):
        return [(x + self.ox, y + self.oy) for x, y in pts]

    def c(self, k):
        return PAL[k] + (255,)

    def poly(self, pts, k):
        self.d.polygon(self._p(pts), fill=self.c(k))

    def rect(self, x0, y0, x1, y1, k):
        self.d.rectangle([x0 + self.ox, y0 + self.oy, x1 + self.ox, y1 + self.oy], fill=self.c(k))

    def line(self, pts, k, w=1):
        self.d.line(self._p(pts), fill=self.c(k), width=w)

    def px(self, x, y, k):
        self.img.putpixel((int(round(x)) + self.ox, int(round(y)) + self.oy), self.c(k))

    def ellipse(self, x0, y0, x1, y1, k):
        self.d.ellipse([x0 + self.ox, y0 + self.oy, x1 + self.ox, y1 + self.oy], fill=self.c(k))


# ---------------------------------------------------------------- shared details

def wheel_side(cv, cx, cy, r=9):
    """A tyre seen side on: black tyre, sidewall shading, a five-spoke alloy."""
    cv.ellipse(cx - r, cy - r, cx + r, cy + r, 'k2')
    cv.ellipse(cx - r + 1, cy - r + 1, cx + r - 1, cy + r - 1, 'k1')
    cv.ellipse(cx - 5, cy - 5, cx + 5, cy + 5, 'c3')
    cv.ellipse(cx - 4, cy - 4, cx + 4, cy + 4, 'c2')
    cv.ellipse(cx - 2, cy - 2, cx + 2, cy + 2, 'c4')
    for dx, dy in ((0, -3), (3, -1), (2, 3), (-2, 3), (-3, -1)):
        cv.px(cx + dx, cy + dy, 'c4')
    cv.px(cx, cy, 'c2')
    cv.px(cx - 2, cy - 4, 'c1')
    cv.px(cx - 3, cy - 3, 'c1')
    cv.px(cx - r + 2, cy - 3, 'k3')


def wheel_end(cv, x0, y0, x1, y1):
    """A tyre seen end on (front / back views)."""
    cv.rect(x0, y0, x1, y1, 'k2')
    cv.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 2, 'k1')
    cv.line([(x0 + 1, y0 + 2), (x0 + 1, y1 - 3)], 'c4')


# ---------------------------------------------------------------- side (facing right)

def side():
    cv = Canvas(8, 12)
    rw, fw = 27, 99                 # wheel centres
    wy = GROUND - 9
    # wheel arches (dark) behind the body
    # lower body
    cv.poly([(9, 53), (7, 49), (7, 40), (9, 35), (14, 33), (79, 33), (100, 35), (113, 37), (119, 39),
             (122, 43), (122, 50), (119, 54), (10, 54)], 'r2')
    # shoulder highlight along the belt and the bonnet
    cv.line([(14, 34), (79, 34)], 'r1')
    cv.line([(80, 34), (100, 36), (113, 38)], 'r1')
    # lower body shade under the crease, sill
    cv.poly([(8, 45), (122, 45), (122, 50), (119, 54), (10, 54), (7, 49)], 'r3')
    cv.line([(12, 44), (120, 44)], 'r4')
    cv.rect(16, 52, 112, 54, 'k1')
    # wheel arches cut into the body
    for cx in (rw, fw):
        cv.ellipse(cx - 12, wy - 12, cx + 12, wy + 12, 'k3')
    cv.rect(0, 55, W - 1, H - 1, 'k3') if False else None
    # greenhouse: pillars in body colour, glass inside
    cv.poly([(15, 33), (24, 16), (60, 14), (67, 15), (81, 33)], 'r3')
    cv.line([(24, 15), (60, 13), (67, 14)], 'r2')
    cv.line([(25, 14), (59, 12)], 'r1')
    # rear quarter window, rear door window, front door window
    cv.poly([(18, 32), (25, 18), (31, 17), (31, 32)], 'g3')
    cv.poly([(34, 32), (34, 17), (52, 16), (52, 32)], 'g3')
    cv.poly([(55, 32), (55, 16), (61, 16), (66, 17), (77, 32)], 'g3')
    # sky reflected in the glass: a light band and a streak
    cv.poly([(34, 22), (52, 19), (52, 23), (34, 26)], 'g2')
    cv.poly([(55, 21), (66, 19), (70, 22), (55, 24)], 'g2')
    cv.poly([(20, 28), (27, 24), (31, 24), (31, 26), (21, 30)], 'g2')
    cv.line([(36, 30), (47, 18)], 'g1')
    cv.line([(58, 30), (64, 18)], 'g1')
    cv.line([(59, 30), (65, 18)], 'g2')
    # window seals
    cv.line([(18, 32), (77, 32)], 'k2')
    # door shut lines and handles
    cv.line([(53, 33), (53, 52)], 'r4')
    cv.line([(32, 33), (32, 50)], 'r4')
    cv.line([(78, 33), (80, 44), (80, 50)], 'r4')
    cv.rect(46, 37, 49, 38, 'k1')
    cv.px(46, 37, 'c3')
    cv.rect(70, 37, 73, 38, 'k1')
    cv.px(70, 37, 'c3')
    # side mirror
    cv.poly([(77, 30), (82, 29), (83, 32), (78, 33)], 'r3')
    cv.line([(78, 30), (82, 29)], 'r1')
    # side rubbing strip
    cv.line([(12, 41), (120, 41)], 'k1')
    cv.line([(12, 40), (120, 40)], 'r1')
    # headlight, indicator, tail light
    cv.poly([(114, 38), (120, 39), (121, 42), (115, 42)], 'y2')
    cv.line([(115, 39), (119, 40)], 'y1')
    cv.rect(118, 43, 121, 44, 'o1')
    cv.poly([(7, 37), (10, 36), (10, 42), (7, 42)], 'l2')
    cv.line([(8, 38), (8, 41)], 'l1')
    # bumpers
    cv.rect(112, 47, 122, 51, 'k1')
    cv.line([(112, 47), (121, 47)], 'c4')
    cv.rect(7, 47, 15, 51, 'k1')
    cv.line([(7, 47), (14, 47)], 'c4')
    # wheels
    wheel_side(cv, rw, wy)
    wheel_side(cv, fw, wy)
    # fuel cap, aerial
    cv.rect(22, 36, 25, 38, 'r3')
    cv.line([(22, 36), (25, 36)], 'r1')
    cv.line([(66, 14), (70, 8)], 'k1')
    return cv.img


# ---------------------------------------------------------------- front (facing the camera)

def front():
    cv = Canvas(8, 12)
    cx = 64
    # tyres under the body
    wheel_end(cv, 37, 49, 45, GROUND)
    wheel_end(cv, 83, 49, 91, GROUND)
    # body: bumper to bonnet
    cv.poly([(36, 54), (36, 42), (39, 36), (89, 36), (92, 42), (92, 54)], 'r2')
    cv.poly([(36, 47), (92, 47), (92, 54), (36, 54)], 'k1')
    cv.line([(36, 47), (92, 47)], 'c4')
    cv.line([(37, 53), (91, 53)], 'k2')
    # bonnet seen from above
    cv.poly([(40, 31), (88, 31), (90, 36), (38, 36)], 'r1')
    cv.line([(40, 31), (88, 31)], 'r2')
    cv.line([(50, 33), (78, 33)], 'r2')
    # grille, badge
    cv.rect(52, 39, 76, 44, 'k2')
    for x in range(54, 76, 3):
        cv.line([(x, 40), (x, 43)], 'k1')
    cv.rect(62, 40, 66, 42, 'c2')
    cv.px(63, 40, 'c1')
    # headlights with indicators outboard
    for x0, x1, ox in ((40, 51, 37), (77, 88, 89)):
        cv.rect(x0, 38, x1, 43, 'c3')
        cv.rect(x0 + 1, 39, x1 - 1, 42, 'y2')
        cv.line([(x0 + 1, 39), (x1 - 2, 39)], 'y1')
        cv.rect(min(ox, ox + 2), 38, max(ox, ox + 2), 42, 'o1')
    cv.rect(37, 38, 39, 42, 'o1')
    cv.rect(89, 38, 91, 42, 'o1')
    # number plate, fog lights
    cv.rect(57, 48, 71, 52, 'p1')
    cv.line([(59, 50), (69, 50)], 'p2')
    cv.rect(40, 49, 44, 50, 'y2')
    cv.rect(84, 49, 88, 50, 'y2')
    # windscreen, A pillars, roof
    cv.poly([(41, 31), (47, 15), (81, 15), (87, 31)], 'r3')
    cv.poly([(43, 30), (49, 17), (79, 17), (85, 30)], 'g3')
    cv.poly([(49, 17), (79, 17), (77, 21), (51, 21)], 'g4')
    cv.poly([(47, 27), (64, 18), (70, 18), (52, 28)], 'g2')
    cv.line([(53, 28), (71, 18)], 'g1')
    cv.line([(44, 30), (84, 30)], 'k2')
    cv.line([(55, 29), (63, 27)], 'k1')            # wipers
    cv.line([(66, 29), (74, 27)], 'k1')
    cv.poly([(47, 15), (81, 15), (80, 12), (48, 12)], 'r2')
    cv.line([(49, 12), (79, 12)], 'r1')
    # mirrors
    cv.poly([(34, 27), (40, 27), (40, 31), (35, 31)], 'r3')
    cv.line([(35, 27), (39, 27)], 'r1')
    cv.poly([(88, 27), (94, 27), (93, 31), (88, 31)], 'r3')
    cv.line([(89, 27), (93, 27)], 'r1')
    return cv.img


# ---------------------------------------------------------------- back

def back():
    cv = Canvas(8, 12)
    wheel_end(cv, 37, 49, 45, GROUND)
    wheel_end(cv, 83, 49, 91, GROUND)
    cv.poly([(36, 54), (36, 40), (38, 33), (90, 33), (92, 40), (92, 54)], 'r2')
    cv.line([(38, 34), (90, 34)], 'r1')
    # bumper, exhaust
    cv.poly([(36, 47), (92, 47), (92, 54), (36, 54)], 'k1')
    cv.line([(36, 47), (92, 47)], 'c4')
    cv.rect(44, 54, 48, 55, 'c3')
    cv.px(44, 54, 'c1')
    # tail lights: red, amber, reversing
    for x0 in (37, 79):
        cv.rect(x0, 36, x0 + 11, 42, 'l3')
        cv.rect(x0 + 1, 37, x0 + 10, 41, 'l2')
        cv.line([(x0 + 1, 37), (x0 + 9, 37)], 'l1')
        cv.rect(x0 + (8 if x0 < 64 else 1), 38, x0 + (10 if x0 < 64 else 3), 40, 'o1')
    cv.rect(46, 43, 50, 45, 'c1')
    cv.rect(78, 43, 82, 45, 'c1')
    # tailgate: plate recess, handle, badge
    cv.rect(55, 39, 73, 45, 'r3')
    cv.rect(57, 40, 71, 44, 'p1')
    cv.line([(59, 42), (69, 42)], 'p2')
    cv.rect(61, 36, 67, 37, 'c3')
    cv.line([(40, 46), (88, 46)], 'r4')
    # rear window (steep hatch) with wiper and brake light
    cv.poly([(39, 33), (46, 16), (82, 16), (89, 33)], 'r3')
    cv.poly([(42, 31), (48, 18), (80, 18), (86, 31)], 'g3')
    cv.poly([(48, 18), (80, 18), (78, 21), (50, 21)], 'g4')
    cv.poly([(50, 28), (66, 19), (71, 19), (55, 29)], 'g2')
    cv.line([(56, 29), (72, 19)], 'g1')
    cv.line([(60, 30), (70, 26)], 'k1')
    cv.rect(58, 17, 70, 17, 'l1')
    cv.poly([(46, 16), (82, 16), (81, 12), (47, 12)], 'r2')
    cv.line([(48, 12), (80, 12)], 'r1')
    # spoiler lip
    cv.line([(46, 15), (82, 15)], 'r4')
    # mirrors behind
    cv.poly([(34, 27), (40, 27), (40, 31), (35, 31)], 'r4')
    cv.poly([(88, 27), (94, 27), (93, 31), (88, 31)], 'r4')
    return cv.img



# ---------------------------------------------------------------- three-quarter views
# Drawn on a guide grid: t runs along the car (0 at the near end), s across it (0 near side),
# h is height in pixels. The near side recedes up and away, the end face the other way.

class Guide:
    def __init__(self, ax, ay, side_dx, side_dy, end_dx, end_dy):
        self.ax, self.ay = ax, ay
        self.sdx, self.sdy, self.edx, self.edy = side_dx, side_dy, end_dx, end_dy

    def __call__(self, t, s, h):
        return (self.ax + t * self.sdx + s * self.edx, self.ay + t * self.sdy + s * self.edy - h)


def wheel_34(cv, g, t, s, wide=6, tall=9, far=False):
    x, y = g(t, s, 9)
    x, y = round(x), round(y)
    cv.ellipse(x - wide, y - tall, x + wide, y + tall, 'k2')
    if far:
        return
    cv.ellipse(x - wide + 1, y - tall + 1, x + wide - 1, y + tall - 1, 'k1')
    cv.ellipse(x - 3, y - 5, x + 3, y + 5, 'c3')
    cv.ellipse(x - 2, y - 4, x + 2, y + 4, 'c2')
    cv.ellipse(x - 1, y - 2, x + 1, y + 2, 'c4')
    for dx, dy in ((0, -3), (2, -1), (1, 3), (-1, 3), (-2, -1)):
        cv.px(x + dx, y + dy, 'c4')
    cv.px(x - 1, y - 4, 'c1')
    cv.px(x - 2, y - 3, 'c1')


def quad(cv, g, pts, k):
    cv.poly([g(*p) for p in pts], k)


def gline(cv, g, pts, k):
    cv.line([g(*p) for p in pts], k)


def front34():
    """Turned toward screen right: the nose on the right, the near side running back up-left."""
    cv = Canvas()
    g = Guide(100, 74, -88, -14, 38, -6)
    wheel_34(cv, g, 0.2, 1.0, far=True)
    # near side: lower body and its shading
    quad(cv, g, [(0, 0, 10), (0, 0, 22), (0.37, 0, 29), (0.93, 0, 29), (1, 0, 27), (1, 0, 10), (0.95, 0, 8), (0.04, 0, 8)], 'r2')
    quad(cv, g, [(0, 0, 10), (0, 0, 17), (1, 0, 17), (1, 0, 10), (0.95, 0, 8), (0.04, 0, 8)], 'r3')
    gline(cv, g, [(0.02, 0, 18), (0.99, 0, 18)], 'r4')
    gline(cv, g, [(0.0, 0, 21), (1.0, 0, 21)], 'k1')
    gline(cv, g, [(0.0, 0, 22), (1.0, 0, 22)], 'r1')
    gline(cv, g, [(0.38, 0, 29), (0.93, 0, 29)], 'r1')
    gline(cv, g, [(0.06, 0, 9), (0.9, 0, 9)], 'k1')
    # wheel arches
    for t in (0.2, 0.83):
        x, y = g(t, 0, 9)
        cv.ellipse(round(x) - 8, round(y) - 12, round(x) + 8, round(y) + 12, 'k3')
    # the nose: bumper, lights, grille
    quad(cv, g, [(0, 0, 8), (0, 1, 8), (0, 1, 22), (0, 0, 22)], 'r2')
    quad(cv, g, [(0, 0, 8), (0, 1, 8), (0, 1, 15), (0, 0, 15)], 'k1')
    gline(cv, g, [(0, 0, 15), (0, 1, 15)], 'c4')
    quad(cv, g, [(0, 0.38, 9), (0, 0.62, 9), (0, 0.62, 13), (0, 0.38, 13)], 'p1')
    gline(cv, g, [(0, 0.42, 11), (0, 0.58, 11)], 'p2')
    for s0, s1 in ((0.04, 0.27), (0.73, 0.96)):
        quad(cv, g, [(0, s0, 17), (0, s1, 17), (0, s1, 22), (0, s0, 22)], 'c3')
        quad(cv, g, [(0, s0 + 0.02, 18), (0, s1 - 0.02, 18), (0, s1 - 0.02, 21), (0, s0 + 0.02, 21)], 'y2')
        gline(cv, g, [(0, s0 + 0.03, 21), (0, s1 - 0.04, 21)], 'y1')
    quad(cv, g, [(0, 0.0, 17), (0, 0.04, 17), (0, 0.04, 21), (0, 0.0, 21)], 'o1')
    quad(cv, g, [(0, 0.96, 17), (0, 1.0, 17), (0, 1.0, 21), (0, 0.96, 21)], 'o1')
    quad(cv, g, [(0, 0.32, 17), (0, 0.68, 17), (0, 0.68, 21), (0, 0.32, 21)], 'k2')
    for s_ in (0.37, 0.43, 0.49, 0.55, 0.61):
        gline(cv, g, [(0, s_, 18), (0, s_, 20)], 'k1')
    quad(cv, g, [(0, 0.46, 18), (0, 0.54, 18), (0, 0.54, 20), (0, 0.46, 20)], 'c2')
    # bonnet from above
    quad(cv, g, [(0, 0, 22), (0, 1, 22), (0.37, 1, 29), (0.37, 0, 29)], 'r1')
    gline(cv, g, [(0, 0, 22), (0, 1, 22)], 'r2')
    gline(cv, g, [(0.15, 0.25, 25), (0.15, 0.75, 25)], 'r2')
    # greenhouse
    quad(cv, g, [(0.37, 0, 29), (0.49, 0.1, 48), (0.86, 0.1, 49), (0.93, 0, 29)], 'r3')
    quad(cv, g, [(0.42, 0.02, 30), (0.5, 0.11, 46), (0.62, 0.11, 46), (0.62, 0.02, 30)], 'g3')
    quad(cv, g, [(0.64, 0.02, 30), (0.64, 0.11, 46), (0.78, 0.11, 46), (0.78, 0.02, 30)], 'g3')
    quad(cv, g, [(0.8, 0.02, 30), (0.8, 0.11, 46), (0.85, 0.11, 46), (0.9, 0.02, 30)], 'g3')
    quad(cv, g, [(0.5, 0.05, 39), (0.62, 0.05, 41), (0.62, 0.05, 37), (0.48, 0.05, 35)], 'g2')
    quad(cv, g, [(0.64, 0.05, 41), (0.78, 0.05, 43), (0.78, 0.05, 39), (0.64, 0.05, 37)], 'g2')
    gline(cv, g, [(0.52, 0.05, 32), (0.58, 0.08, 44)], 'g1')
    gline(cv, g, [(0.66, 0.05, 32), (0.72, 0.08, 44)], 'g1')
    gline(cv, g, [(0.42, 0.02, 30), (0.9, 0.02, 30)], 'k2')
    # windscreen and roof
    quad(cv, g, [(0.37, 0, 29), (0.37, 1, 29), (0.49, 0.9, 48), (0.49, 0.1, 48)], 'r3')
    quad(cv, g, [(0.38, 0.06, 30), (0.38, 0.94, 30), (0.48, 0.86, 46), (0.48, 0.14, 46)], 'g3')
    quad(cv, g, [(0.46, 0.14, 44), (0.48, 0.14, 46), (0.48, 0.86, 46), (0.46, 0.86, 44)], 'g4')
    quad(cv, g, [(0.39, 0.25, 31), (0.47, 0.5, 45), (0.47, 0.62, 45), (0.39, 0.37, 31)], 'g2')
    gline(cv, g, [(0.39, 0.4, 31), (0.47, 0.66, 45)], 'g1')
    gline(cv, g, [(0.38, 0.06, 30), (0.38, 0.94, 30)], 'k2')
    gline(cv, g, [(0.385, 0.25, 31), (0.385, 0.45, 32)], 'k1')
    gline(cv, g, [(0.385, 0.55, 31), (0.385, 0.75, 32)], 'k1')
    quad(cv, g, [(0.49, 0.1, 48), (0.49, 0.9, 48), (0.86, 0.9, 49), (0.86, 0.1, 49)], 'r2')
    gline(cv, g, [(0.5, 0.1, 49), (0.85, 0.1, 50)], 'r1')
    gline(cv, g, [(0.84, 0.3, 49), (0.9, 0.3, 57)], 'k1')         # aerial
    # door lines, handles, mirror
    gline(cv, g, [(0.63, 0, 29), (0.63, 0, 10)], 'r4')
    gline(cv, g, [(0.38, 0, 29), (0.36, 0, 18), (0.36, 0, 10)], 'r4')
    gline(cv, g, [(0.8, 0, 29), (0.8, 0, 12)], 'r4')
    quad(cv, g, [(0.57, 0, 25), (0.6, 0, 25), (0.6, 0, 26), (0.57, 0, 26)], 'k1')
    quad(cv, g, [(0.75, 0, 25), (0.78, 0, 25), (0.78, 0, 26), (0.75, 0, 26)], 'k1')
    quad(cv, g, [(0.36, -0.08, 29), (0.4, -0.08, 29), (0.4, -0.08, 33), (0.36, -0.08, 33)], 'r3')
    gline(cv, g, [(0.36, -0.08, 33), (0.4, -0.08, 33)], 'r1')
    # side lights and tail light at the far end
    quad(cv, g, [(0.98, 0, 22), (1, 0, 22), (1, 0, 27), (0.98, 0, 27)], 'l2')
    quad(cv, g, [(0.0, 0, 17), (0.02, 0, 17), (0.02, 0, 21), (0.0, 0, 21)], 'o1')
    wheel_34(cv, g, 0.2, 0)
    wheel_34(cv, g, 0.83, 0)
    return cv.img


def back34():
    """Turned away toward screen right: the tail on the left, the near side running forward up-right."""
    cv = Canvas()
    g = Guide(44, 74, 88, -14, -38, -6)
    # near side (t = 0 is the tail)
    quad(cv, g, [(0, 0, 10), (0, 0, 27), (0.07, 0, 29), (0.63, 0, 29), (1, 0, 22), (1, 0, 10), (0.96, 0, 8), (0.05, 0, 8)], 'r2')
    quad(cv, g, [(0, 0, 10), (0, 0, 17), (1, 0, 17), (1, 0, 10), (0.96, 0, 8), (0.05, 0, 8)], 'r3')
    gline(cv, g, [(0.01, 0, 18), (0.98, 0, 18)], 'r4')
    gline(cv, g, [(0.0, 0, 21), (1.0, 0, 21)], 'k1')
    gline(cv, g, [(0.0, 0, 22), (1.0, 0, 22)], 'r1')
    gline(cv, g, [(0.07, 0, 29), (0.62, 0, 29)], 'r1')
    gline(cv, g, [(0.08, 0, 9), (0.94, 0, 9)], 'k1')
    for t in (0.17, 0.8):
        x, y = g(t, 0, 9)
        cv.ellipse(round(x) - 8, round(y) - 12, round(x) + 8, round(y) + 12, 'k3')
    # the tail: bumper, lights, tailgate, plate
    quad(cv, g, [(0, 0, 8), (0, 1, 8), (0, 1, 27), (0, 0, 27)], 'r2')
    gline(cv, g, [(0, 0, 26), (0, 1, 26)], 'r1')
    quad(cv, g, [(0, 0, 8), (0, 1, 8), (0, 1, 15), (0, 0, 15)], 'k1')
    gline(cv, g, [(0, 0, 15), (0, 1, 15)], 'c4')
    for s0, s1 in ((0.02, 0.22), (0.78, 0.98)):
        quad(cv, g, [(0, s0, 18), (0, s1, 18), (0, s1, 25), (0, s0, 25)], 'l3')
        quad(cv, g, [(0, s0 + 0.02, 19), (0, s1 - 0.02, 19), (0, s1 - 0.02, 24), (0, s0 + 0.02, 24)], 'l2')
        gline(cv, g, [(0, s0 + 0.03, 24), (0, s1 - 0.03, 24)], 'l1')
    quad(cv, g, [(0, 0.3, 16), (0, 0.7, 16), (0, 0.7, 23), (0, 0.3, 23)], 'r3')
    quad(cv, g, [(0, 0.34, 17), (0, 0.66, 17), (0, 0.66, 21), (0, 0.34, 21)], 'p1')
    gline(cv, g, [(0, 0.38, 19), (0, 0.62, 19)], 'p2')
    quad(cv, g, [(0, 0.85, 7), (0, 0.93, 7), (0, 0.93, 8), (0, 0.85, 8)], 'c3')
    # hatch glass, roof
    quad(cv, g, [(0, 0, 27), (0, 1, 27), (0.15, 0.9, 49), (0.15, 0.1, 49)], 'r3')
    quad(cv, g, [(0.01, 0.07, 29), (0.01, 0.93, 29), (0.14, 0.86, 46), (0.14, 0.14, 46)], 'g3')
    quad(cv, g, [(0.12, 0.14, 44), (0.14, 0.14, 46), (0.14, 0.86, 46), (0.12, 0.86, 44)], 'g4')
    quad(cv, g, [(0.02, 0.3, 30), (0.12, 0.55, 45), (0.12, 0.66, 45), (0.02, 0.41, 30)], 'g2')
    gline(cv, g, [(0.02, 0.44, 30), (0.12, 0.7, 45)], 'g1')
    gline(cv, g, [(0.015, 0.35, 31), (0.05, 0.6, 35)], 'k1')
    quad(cv, g, [(0.15, 0.3, 48), (0.15, 0.7, 48), (0.15, 0.7, 49), (0.15, 0.3, 49)], 'l1')
    quad(cv, g, [(0.15, 0.1, 49), (0.15, 0.9, 49), (0.5, 0.9, 48), (0.5, 0.1, 48)], 'r2')
    gline(cv, g, [(0.16, 0.1, 50), (0.5, 0.1, 49)], 'r1')
    gline(cv, g, [(0.18, 0.3, 49), (0.24, 0.3, 57)], 'k1')
    # side glass and pillars
    quad(cv, g, [(0.07, 0, 29), (0.15, 0.1, 49), (0.5, 0.1, 48), (0.63, 0, 29)], 'r3')
    quad(cv, g, [(0.1, 0.02, 30), (0.16, 0.11, 46), (0.21, 0.11, 46), (0.21, 0.02, 30)], 'g3')
    quad(cv, g, [(0.23, 0.02, 30), (0.23, 0.11, 46), (0.37, 0.11, 46), (0.37, 0.02, 30)], 'g3')
    quad(cv, g, [(0.39, 0.02, 30), (0.39, 0.11, 46), (0.49, 0.11, 46), (0.58, 0.02, 30)], 'g3')
    quad(cv, g, [(0.23, 0.05, 37), (0.37, 0.05, 39), (0.37, 0.05, 43), (0.23, 0.05, 41)], 'g2')
    quad(cv, g, [(0.39, 0.05, 37), (0.49, 0.05, 39), (0.5, 0.05, 43), (0.39, 0.05, 41)], 'g2')
    gline(cv, g, [(0.26, 0.05, 32), (0.32, 0.08, 44)], 'g1')
    gline(cv, g, [(0.42, 0.05, 32), (0.47, 0.08, 44)], 'g1')
    gline(cv, g, [(0.1, 0.02, 30), (0.58, 0.02, 30)], 'k2')
    # door lines, handles, mirror, fuel cap
    gline(cv, g, [(0.38, 0, 29), (0.38, 0, 10)], 'r4')
    gline(cv, g, [(0.22, 0, 29), (0.22, 0, 12)], 'r4')
    gline(cv, g, [(0.62, 0, 29), (0.64, 0, 18), (0.64, 0, 10)], 'r4')
    quad(cv, g, [(0.31, 0, 25), (0.34, 0, 25), (0.34, 0, 26), (0.31, 0, 26)], 'k1')
    quad(cv, g, [(0.53, 0, 25), (0.56, 0, 25), (0.56, 0, 26), (0.53, 0, 26)], 'k1')
    quad(cv, g, [(0.1, 0, 23), (0.13, 0, 23), (0.13, 0, 25), (0.1, 0, 25)], 'r3')
    quad(cv, g, [(0.61, -0.08, 29), (0.65, -0.08, 29), (0.65, -0.08, 33), (0.61, -0.08, 33)], 'r3')
    gline(cv, g, [(0.61, -0.08, 33), (0.65, -0.08, 33)], 'r1')
    # far end: the nose's side marker
    quad(cv, g, [(0.98, 0, 17), (1, 0, 17), (1, 0, 21), (0.98, 0, 21)], 'o1')
    wheel_34(cv, g, 0.17, 0)
    wheel_34(cv, g, 0.8, 0)
    return cv.img


# ---------------------------------------------------------------- outline and sheet

def outline(img):
    """Selective outline: a dark shade of the colour beside it, all round the silhouette."""
    px = img.load()
    out = img.copy()
    o = out.load()
    for y in range(H):
        for x in range(W):
            if px[x, y][3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                xx, yy = x + dx, y + dy
                if 0 <= xx < W and 0 <= yy < H and px[xx, yy][3]:
                    r, g, b, _ = px[xx, yy]
                    k = 0.35
                    o[x, y] = (int(r * k), int(g * k), int(b * k), 255)
                    break
    return out


def mirror(img):
    return img.transpose(Image.FLIP_LEFT_RIGHT)


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else 'build/art'
    os.makedirs(out, exist_ok=True)
    views = {'front': front(), 'front34': front34(), 'side': side(), 'back34': back34(), 'back': back()}
    views = {k: outline(v) for k, v in views.items()}
    # S, SE, E, NE, N, NW, W, SW (the car's facing seen from the camera)
    order = [('S', views['front']), ('SE', views['front34']), ('E', views['side']), ('NE', views['back34']),
             ('N', views['back']), ('NW', mirror(views['back34'])), ('W', mirror(views['side'])), ('SW', mirror(views['front34']))]
    cols = 4
    sheet = Image.new('RGBA', (W * cols, H * 2), (0, 0, 0, 0))
    for i, (_, v) in enumerate(order):
        sheet.paste(v, ((i % cols) * W, (i // cols) * H))
    sheet.save(os.path.join(out, 'car_views.png'))
    scale = 4
    bg = Image.new('RGB', (sheet.width * scale, sheet.height * scale), (104, 108, 116))
    big = sheet.resize((sheet.width * scale, sheet.height * scale), Image.NEAREST)
    bg.paste(big, (0, 0), big)
    bg.save(os.path.join(out, 'car_views_preview.png'))
    print('ok')


if __name__ == '__main__':
    main()
