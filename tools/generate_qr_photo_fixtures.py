#!/usr/bin/env python3
"""
Generates a folder of synthetic "photographed QR code" images to test how well
su.knst.crypto.utils.codes.SimpleQRCodeWorker (the `qr scan` command) copes with
QR codes the way a phone camera would actually hand them over: tilted in 3D space,
farther away / closer, out of focus, noisy, unevenly lit - not just a clean render.

Each QR's payload is a random alphanumeric string, and the output file is named
after that exact payload (e.g. "AbC123...xyz.png"), so the Java-side test can just
assert decoded_text == filename with no separate ground-truth file needed.

Usage:
    python3 tools/generate_qr_photo_fixtures.py [output_dir] [count]

Requires: qrcode, Pillow, numpy
    python3 -m pip install --user qrcode pillow numpy
"""
import math
import os
import random
import string
import sys

import numpy as np
import qrcode
from PIL import Image, ImageFilter, ImageEnhance, ImageDraw

OUTPUT_DIR = sys.argv[1] if len(sys.argv) > 1 else "src/test/resources/qr_photo_fixtures"
COUNT = int(sys.argv[2]) if len(sys.argv) > 2 else 100

CANVAS_SIZE = 900
EC_LEVELS = [
    qrcode.constants.ERROR_CORRECT_L,
    qrcode.constants.ERROR_CORRECT_M,
    qrcode.constants.ERROR_CORRECT_Q,
    qrcode.constants.ERROR_CORRECT_H,
]


def random_payload():
    length = random.randint(10, 48)
    alphabet = string.ascii_letters + string.digits
    return "".join(random.choice(alphabet) for _ in range(length))


def make_qr_image(payload):
    qr = qrcode.QRCode(
        error_correction=random.choice(EC_LEVELS),
        box_size=random.randint(8, 14),
        border=random.choice([2, 3, 4]),
    )
    qr.add_data(payload)
    qr.make(fit=True)

    return qr.make_image(fill_color="black", back_color="white").convert("RGBA")


def find_coeffs(source_pts, dest_pts):
    """Perspective coefficients for Image.transform(..., Image.PERSPECTIVE, coeffs):
    maps points in `dest_pts` (output/canvas space) back to `source_pts` (source image space)."""
    matrix = []
    for (sx, sy), (dx, dy) in zip(source_pts, dest_pts):
        matrix.append([dx, dy, 1, 0, 0, 0, -sx * dx, -sx * dy])
        matrix.append([0, 0, 0, dx, dy, 1, -sy * dx, -sy * dy])

    a = np.array(matrix, dtype=float)
    b = np.array(source_pts, dtype=float).reshape(8)

    return np.linalg.solve(a, b)


def project_corners(w, h, tilt_x_deg, tilt_y_deg, rot_z_deg, on_canvas_half, center, camera_distance):
    """Projects the 4 corners of a flat w x h rectangle, tilted in 3D, onto the 2D canvas -
    a simple pinhole-camera model of a piece of paper held at an angle to the camera."""
    hw, hh = w / 2, h / 2
    corners = [(-hw, -hh, 0.0), (hw, -hh, 0.0), (hw, hh, 0.0), (-hw, hh, 0.0)]  # TL, TR, BR, BL

    tx, ty, tz = (math.radians(a) for a in (tilt_x_deg, tilt_y_deg, rot_z_deg))
    scale = on_canvas_half / max(hw, hh)
    cx, cy = center

    projected = []
    for x, y, z in corners:
        # in-plane rotation (Z), then tilt up/down (X), then tilt left/right (Y)
        x, y = x * math.cos(tz) - y * math.sin(tz), x * math.sin(tz) + y * math.cos(tz)
        y, z = y * math.cos(tx) - z * math.sin(tx), y * math.sin(tx) + z * math.cos(tx)
        x, z = x * math.cos(ty) + z * math.sin(ty), -x * math.sin(ty) + z * math.cos(ty)

        x *= scale
        y *= scale

        px = x * camera_distance / (camera_distance + z) + cx
        py = y * camera_distance / (camera_distance + z) + cy
        projected.append((px, py))

    return projected


def random_background(size):
    # a plausible desk/table/paper-adjacent surface: solid-ish tone with a soft lighting
    # gradient and a bit of texture noise, not a flat studio white
    base = tuple(random.randint(120, 235) for _ in range(3))
    bg = Image.new("RGB", (size, size), base)

    gradient = np.zeros((size, size), dtype=np.float32)
    angle = random.uniform(0, 2 * math.pi)
    gx, gy = math.cos(angle), math.sin(angle)
    xs, ys = np.meshgrid(np.linspace(-1, 1, size), np.linspace(-1, 1, size))
    gradient = (xs * gx + ys * gy)
    gradient = (gradient - gradient.min()) / (gradient.max() - gradient.min())
    strength = random.uniform(-40, 40)

    arr = np.array(bg, dtype=np.float32)
    for c in range(3):
        arr[:, :, c] += gradient * strength
    arr += np.random.normal(0, 4, arr.shape)
    arr = np.clip(arr, 0, 255).astype(np.uint8)

    return Image.fromarray(arr, mode="RGB")


def compose_photo(payload):
    qr_img = make_qr_image(payload)
    w, h = qr_img.size

    canvas_center = (CANVAS_SIZE / 2 + random.uniform(-60, 60), CANVAS_SIZE / 2 + random.uniform(-60, 60))

    # "distance": how large a fraction of the frame the code occupies
    difficulty = random.random()
    if difficulty < 0.35:
        on_canvas_half = CANVAS_SIZE * random.uniform(0.32, 0.46)  # close / easy
    elif difficulty < 0.75:
        on_canvas_half = CANVAS_SIZE * random.uniform(0.18, 0.32)  # medium distance
    else:
        on_canvas_half = CANVAS_SIZE * random.uniform(0.07, 0.18)  # far away / hard

    tilt_x = random.uniform(-40, 40)
    tilt_y = random.uniform(-40, 40)
    rot_z = random.uniform(-180, 180)
    camera_distance = random.uniform(700, 2500)

    dest_quad = project_corners(w, h, tilt_x, tilt_y, rot_z, on_canvas_half, canvas_center, camera_distance)
    coeffs = find_coeffs([(0, 0), (w, 0), (w, h), (0, h)], dest_quad)

    warped = qr_img.transform(
        (CANVAS_SIZE, CANVAS_SIZE), Image.PERSPECTIVE, coeffs,
        resample=Image.BICUBIC, fillcolor=(0, 0, 0, 0)
    )

    photo = random_background(CANVAS_SIZE).convert("RGBA")
    photo = Image.alpha_composite(photo, warped).convert("RGB")

    # focus / motion blur
    blur_radius = random.choice([0, 0, 0.5, 1, 1.5, 2.5, 4])
    if blur_radius:
        photo = photo.filter(ImageFilter.GaussianBlur(blur_radius))

    # brightness / contrast jitter, like auto-exposure guessing wrong
    photo = ImageEnhance.Brightness(photo).enhance(random.uniform(0.65, 1.35))
    photo = ImageEnhance.Contrast(photo).enhance(random.uniform(0.6, 1.25))

    # sensor noise
    noise_sigma = random.choice([0, 2, 5, 10, 18])
    if noise_sigma:
        arr = np.array(photo, dtype=np.float32)
        arr += np.random.normal(0, noise_sigma, arr.shape)
        photo = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), mode="RGB")

    # simulate whatever resolution the camera/upload pipeline ends up at
    final_size = random.choice([300, 450, 600, 720, 900, 1100])
    if final_size != CANVAS_SIZE:
        photo = photo.resize((final_size, final_size), Image.LANCZOS)

    return photo


def main():
    random.seed()
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    for existing in os.listdir(OUTPUT_DIR):
        os.remove(os.path.join(OUTPUT_DIR, existing))

    generated = 0
    attempts = 0
    while generated < COUNT and attempts < COUNT * 3:
        attempts += 1
        payload = random_payload()
        photo = compose_photo(payload)

        path = os.path.join(OUTPUT_DIR, f"{payload}.jpg")
        photo.save(path, "JPEG", quality=random.randint(55, 95))

        generated += 1

    print(f"Generated {generated} fixture photos in {OUTPUT_DIR}")


if __name__ == "__main__":
    main()
