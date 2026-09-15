import os
try:
    from PIL import Image
except ImportError:
    os.system("pip install Pillow")
    from PIL import Image

def pad_to_square_safe_zone(input_path, output_path, logo_fraction=0.55):
    img = Image.open(input_path).convert("RGBA")
    w, h = img.size
    
    # Calculate canvas size so that the maximum dimension of the logo
    # takes up exactly `logo_fraction` of the new canvas.
    max_dim = max(w, h)
    canvas_size = int(max_dim / logo_fraction)
    
    # Create a new transparent square canvas
    canvas = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    
    # Calculate top-left position to center the logo
    x = (canvas_size - w) // 2
    y = (canvas_size - h) // 2
    
    canvas.paste(img, (x, y))
    canvas.save(output_path)
    print(f"Padded {input_path} -> {output_path} (Canvas: {canvas_size}x{canvas_size})")

src = "rtc_logo_transparent(1).png"
pad_to_square_safe_zone(src, "app/src/main/res/drawable-nodpi/rtc_logo_mark_transparent.png")
pad_to_square_safe_zone(src, "app/src/main/res/drawable-nodpi/rtc_community_logo_transparent.png")
