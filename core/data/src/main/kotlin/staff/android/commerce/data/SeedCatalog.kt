package staff.android.commerce.data

import staff.android.commerce.model.Product

/** Fictional merchandise and prices, bundled offline so the starting journey needs no account. */
internal fun seedCatalog(): List<Product> = listOf(
    Product("bag", "Everyday bag", "Carry a little less. Do a little more.",
        "A considered companion for the everyday: soft canvas, comfortable straps, and room for the things you reach for most.",
        "Carry", 249900, "bag", 6, "BESTSELLER"),
    Product("headphones", "Studio headphones", "Find your quiet.",
        "Over-ear headphones with a comfortable padded fit. A simple companion for focus, your favourite album, or the journey home.",
        "Tech", 799900, "headphones", 4, "NEW ARRIVAL"),
    Product("tea", "Tea", "A slower start to your morning.",
        "Loose-leaf breakfast tea with a rounded, comforting flavour. Brew a pot, take a breath, and make a small ritual of it. 100 g.",
        "Pantry", 49900, "tea", 4),
    Product("camera", "Camera", "Keep the moments that matter.",
        "A compact everyday camera with a tactile grip and a simple silhouette. Made for weekend walks and the unexpected details along the way.",
        "Tech", 4299900, "camera", 2, "THE WEEKEND EDIT"),
    Product("lamp", "Arc table lamp", "A warmer corner.",
        "A softly curved desk lamp that brings a warm pool of light to your bedside table, reading nook, or evening workspace.",
        "Home", 329900, "lamp", 5),
    Product("mug", "Stoneware mug", "A good cup deserves a good home.",
        "A glazed stoneware mug with a generous handle and a comfortable weight. Subtle variations in the finish make each cup feel personal.",
        "Home", 79900, "mug", 8),
)
