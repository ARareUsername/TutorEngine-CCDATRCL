#!/usr/bin/env python3
"""
generate_dataset.py - TutorEngine MTG Dataset Generator
Streams authentic Magic: The Gathering singles from Scryfall Oracle bulk data,
formats them into TutorEngine's exact 16-column CSV schema, and outputs both:
  - Dataset/cards.csv (expanded 1000+ card inventory, preserving original 60 cards at top)
  - Dataset/cards_1000.csv (standalone 1000+ benchmark dataset)

Documented generation rules (PROJECT_SPECS.md §6, §11, Template C):
- Source: Scryfall Oracle Cards bulk JSONL (paper English tournament-legal printings)
- Deterministic pseudo-random seeding for quantity, demand, and foil variance
- Preserves original 60 cards at lines 1-61 verbatim to maintain unit test / trace packet parity
"""

import csv
import gzip
import json
import os
import random
import re
import sys
import urllib.parse
import urllib.request

SCRYFALL_ORACLE_URL = "https://data.scryfall.io/oracle-cards/oracle-cards-20261011210156.jsonl.gz"
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
ORIGINAL_CSV = os.path.join(SCRIPT_DIR, "cards.csv")
TARGET_1000_CSV = os.path.join(SCRIPT_DIR, "cards_1000.csv")

COLOR_MAP = {
    "W": "White",
    "U": "Blue",
    "B": "Black",
    "R": "Red",
    "G": "Green"
}

def determine_color(colors):
    if not colors:
        return "Colorless"
    if len(colors) == 1:
        return COLOR_MAP.get(colors[0], "Colorless")
    return "Multi"

def determine_type(type_line):
    if not type_line:
        return "Card"
    tl = type_line.lower()
    if "creature" in tl:
        return "Creature"
    if "instant" in tl:
        return "Instant"
    if "sorcery" in tl:
        return "Sorcery"
    if "enchantment" in tl:
        return "Enchantment"
    if "artifact" in tl:
        return "Artifact"
    if "planeswalker" in tl:
        return "Planeswalker"
    if "land" in tl:
        return "Land"
    if "battle" in tl:
        return "Battle"
    return "Card"

def sanitize_csv_value(val):
    if val is None:
        return ""
    s = str(val).strip()
    return s

def generate(target_count=1050):
    print(f"Reading existing cards from {ORIGINAL_CSV}...")
    existing_rows = []
    seen_skus = set()
    seen_names = set()

    if os.path.exists(ORIGINAL_CSV):
        with open(ORIGINAL_CSV, "r", encoding="utf-8") as f:
            reader = csv.reader(f)
            header = next(reader, None)
            for row in reader:
                if not row or len(row) < 15:
                    continue
                existing_rows.append(row)
                seen_skus.add(row[0].strip().upper())
                seen_names.add(row[1].strip().lower())

    print(f"Loaded {len(existing_rows)} existing baseline cards.")

    # Target total = target_count (default 1050 cards)
    needed = max(0, target_count - len(existing_rows))
    print(f"Streaming {needed} additional real cards from Scryfall Oracle bulk data...")

    req = urllib.request.Request(SCRYFALL_ORACLE_URL, headers={"User-Agent": "TutorEngine/1.0"})
    new_rows = []
    rng = random.Random(42)  # Deterministic seed for reproducible dataset

    with urllib.request.urlopen(req) as resp:
        with gzip.GzipFile(fileobj=resp) as gz:
            for line in gz:
                if len(new_rows) >= needed:
                    break
                try:
                    card = json.loads(line.decode("utf-8"))
                except Exception:
                    continue

                # Filter valid paper singles
                if card.get("lang") != "en":
                    continue
                if card.get("digital", False):
                    continue
                set_type = card.get("set_type", "")
                if set_type not in ("expansion", "core", "masters", "draft_innovation", "commander"):
                    continue
                layout = card.get("layout", "")
                if layout in ("art_series", "token", "double_faced_token", "emblem", "vanguard"):
                    continue

                name = card.get("name", "").strip()
                if not name or name.lower() in seen_names:
                    continue

                set_code = card.get("set", "").strip().upper()
                c_num = card.get("collector_number", "").strip()
                if not set_code or not c_num:
                    continue

                # Clean collector number (digits only for clean SKU)
                clean_num = re.sub(r"[^0-9A-Za-z]", "", c_num)
                sku = f"MTG-{set_code}-{clean_num}"
                if sku.upper() in seen_skus:
                    continue

                set_name = card.get("set_name", set_code).strip()
                color = determine_color(card.get("colors", []))
                card_type = determine_type(card.get("type_line", ""))

                # Release year
                rel = card.get("released_at", "2024")
                try:
                    year = int(rel[:4])
                except Exception:
                    year = 2024

                # Price in USD
                prices = card.get("prices", {}) or {}
                usd_str = prices.get("usd")
                rarity = card.get("rarity", "common").lower()
                if usd_str:
                    try:
                        price = round(float(usd_str), 2)
                    except Exception:
                        price = 0.50
                else:
                    # Realistic baseline prices based on rarity
                    if rarity == "mythic":
                        price = round(rng.uniform(3.50, 18.00), 2)
                    elif rarity == "rare":
                        price = round(rng.uniform(1.20, 6.50), 2)
                    elif rarity == "uncommon":
                        price = round(rng.uniform(0.30, 1.25), 2)
                    else:
                        price = round(rng.uniform(0.10, 0.45), 2)

                if price <= 0:
                    price = 0.25

                # Quantity (weighted: some low stock for Restock demo, some bulk stock)
                q_roll = rng.random()
                if q_roll < 0.15:
                    qty = rng.randint(1, 3)  # low stock
                elif q_roll < 0.35:
                    qty = rng.randint(4, 9)
                else:
                    qty = rng.randint(10, 36)

                # Demand score 1-100
                edhrec = card.get("edhrec_rank")
                if edhrec is not None and edhrec > 0:
                    # Inverse log scale: top cards have high demand
                    demand = max(15, min(99, int(100 - (edhrec / 350.0))))
                else:
                    demand = rng.randint(25, 90)

                # Foil
                is_foil = (prices.get("usd_foil") is not None and rng.random() < 0.35) or (rng.random() < 0.12)

                # Box location
                box_loc = f"{set_code}-1"

                # Mana cost, power, toughness
                mana_cost = card.get("mana_cost", "")
                power = card.get("power", "")
                toughness = card.get("toughness", "")

                # Image URL
                image_uris = card.get("image_uris", {}) or {}
                image_url = image_uris.get("small") or image_uris.get("normal")
                if not image_url:
                    faces = card.get("card_faces", [])
                    if faces and isinstance(faces, list) and "image_uris" in faces[0]:
                        image_url = faces[0]["image_uris"].get("small")

                if not image_url:
                    encoded_name = urllib.parse.quote(name)
                    image_url = f"https://api.scryfall.com/cards/named?exact={encoded_name}&format=image&version=small&set={set_code.lower()}"

                new_row = [
                    sku,
                    name,
                    set_name,
                    color,
                    card_type,
                    str(year),
                    f"{price:.2f}",
                    str(qty),
                    str(demand),
                    "true" if is_foil else "false",
                    box_loc,
                    mana_cost,
                    power,
                    toughness,
                    set_code,
                    image_url
                ]

                new_rows.append(new_row)
                seen_skus.add(sku.upper())
                seen_names.add(name.lower())

    all_rows = existing_rows + new_rows
    print(f"Total cards ready: {len(all_rows)} ({len(existing_rows)} baseline + {len(new_rows)} Scryfall additions)")

    csv_header = [
        "sku", "name", "setName", "color", "cardType", "releaseYear",
        "price", "quantity", "demandScore", "isFoil", "boxLocationId",
        "manaCost", "power", "toughness", "setCode", "imageUrl"
    ]

    # Write Dataset/cards_1000.csv
    with open(TARGET_1000_CSV, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(csv_header)
        writer.writerows(all_rows)
    print(f"Wrote {len(all_rows)} cards to {TARGET_1000_CSV}")

    # Write Dataset/cards.csv (primary database)
    with open(ORIGINAL_CSV, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(csv_header)
        writer.writerows(all_rows)
    print(f"Updated primary {ORIGINAL_CSV} with {len(all_rows)} cards.")

if __name__ == "__main__":
    count = 1050
    if len(sys.argv) > 1:
        try:
            count = int(sys.argv[1])
        except ValueError:
            pass
    generate(count)
