-- Enchanted Veinminer icon sprites. Run through the aseprite MCP: dofile("<abs>/EnchantedVeinminer/dev/icon/draw_sprites.lua")
dofile("/home/emppu/Projects/Minecraft Datapacks/.claude/skills/pack-icon-animation/assets/pixel_art.lua")
local OUT = "/home/emppu/Projects/Minecraft Datapacks/EnchantedVeinminer/dev/icon/sprites/"
local VM = "/home/emppu/Projects/Minecraft Datapacks/Veinminer/dev/icon/sprites/"
local pc = app.pixelColor

local stone = { l = "#C1C3C7", m = "#9EA1AC", k = "#797C94" }
local lapis = { F = "#CFE6FF", E = "#7EAEFF", D = "#4579EA", C = "#2F4FC7", B = "#22308F", A = "#17144F" }
local book = { a = "#2E0A14", b = "#5C1526", c = "#8E2436", d = "#B8373F", e = "#D9584F",
               i = "#B3561A", g = "#FBB829", h = "#FFE14D", p = "#F4EAD0", q = "#D8C79E", r = "#A8916A" }
local GLINT_EDGE, GLINT_CORE = "#C866FF", "#F6C8FF"

local function merge(...)
  local out = {}
  for _, t in ipairs({ ... }) do for k, v in pairs(t) do out[k] = v end end
  return out
end
local function hexof(v) return string.format("#%02X%02X%02X", pc.rgbaR(v), pc.rgbaG(v), pc.rgbaB(v)) end
local function lerp(hex, to, k)
  local a, b = PA.hex(hex), PA.hex(to)
  local f = function(x, y) return math.floor(x + (y - x) * k + 0.5) end
  return string.format("#%02X%02X%02X", f(pc.rgbaR(a), pc.rgbaR(b)), f(pc.rgbaG(a), pc.rgbaG(b)), f(pc.rgbaB(a), pc.rgbaB(b)))
end
-- read a png/aseprite into {key = hex}, w, h
local function load(path)
  local s = Sprite{ fromFile = path }
  local px = {}
  for it in s.cels[1].image:pixels() do
    local v = it()
    if pc.rgbaA(v) > 127 then px[PA.key(it.x + s.cels[1].position.x, it.y + s.cels[1].position.y)] = hexof(v) end
  end
  local w, h = s.width, s.height
  s:close()
  return px, w, h
end
local function transform(src, dst, fn)
  local px, w, h = load(src)
  local out = {}
  for k, v in pairs(px) do out[k] = fn(k % 4096, k // 4096, v) end
  PA.save_pixels(dst, w, h, out)
end
-- enchantment glint: a diagonal band (x - y in [c, c + 3]) lerped toward violet, brightest in the middle two
local function glint(src, dst, c)
  transform(src, dst, function(x, y, v)
    local d = (x - y) - c
    if d == 1 or d == 2 then return lerp(v, GLINT_CORE, 0.75) end
    if d == 0 or d == 3 then return lerp(v, GLINT_EDGE, 0.55) end
    return v
  end)
end
-- stamp small grids onto a base grid (rows of chars); '.' keeps the base
local function stamp(rows, blob, x0, y0)
  for y = 1, #blob do
    local row = rows[y0 + y]
    for x = 1, #blob[y] do
      local ch = blob[y]:sub(x, x)
      if ch ~= "." then row = row:sub(1, x0 + x - 1) .. ch .. row:sub(x0 + x + 1) end
    end
    rows[y0 + y] = row
  end
end

-- lapis ore: stone with a 1px bevel (light top/left, dark bottom/right), a crack band, five lapis flakes
local ore = {
  "lllllllllllllllm",
  "lmmmmmmmmmmmmmmk",
  "lmmmmmmmmmmmmmmk",
  "lmmmmmmmmmmmmmmk",
  "lmmmmmmmmmmmmmmk",
  "lmmmmmmmmmmmmmmk",
  "lmmmmmmmmmmmmmmk",
  "lmllmmmmlllmmllk",
  "lmkkmmmmkkkmmkkk",
  "lmmmmmmmmmmmmmmk",
  "lmmmmmmmmmmmmmmk",
  "lmmmmmmmmmmmmmmk",
  "lmmmmmmmmmmmmmmk",
  "lmmmmmmmmmmmmmmk",
  "lmmmmmllmmmmmmmk",
  "mkkkkkkkkkkkkkkk",
}
stamp(ore, { ".EE...", "EFDDC.", "EDDCBA", ".DCBA.", "..AA.." }, 2, 1)
stamp(ore, { ".ED.", "EDCA", ".CA." }, 10, 1)
stamp(ore, { "DC", "CA" }, 12, 5)
stamp(ore, { ".ED..", "EDDCA", ".CCA." }, 3, 9)
stamp(ore, { "..EE.", ".EFDC", "EDDCA", "DCCBA", ".AA.." }, 9, 9)
stamp(ore, { "ED", "CA" }, 7, 12)
PA.sprite_from_grid(OUT .. "ore_top", ore, merge(stone, lapis))
PA.remap(OUT .. "ore_top.aseprite", OUT .. "ore_side_left", {
  ["#C1C3C7"] = "#9EA1AC", ["#9EA1AC"] = "#797C94", ["#797C94"] = "#5A5A77",
  ["#CFE6FF"] = "#7EAEFF", ["#7EAEFF"] = "#4579EA", ["#4579EA"] = "#2F4FC7", ["#2F4FC7"] = "#22308F",
  ["#22308F"] = "#17144F", ["#17144F"] = "#0E0C33",
})
PA.remap(OUT .. "ore_top.aseprite", OUT .. "ore_side_right", {
  ["#C1C3C7"] = "#797C94", ["#9EA1AC"] = "#5A5A77", ["#797C94"] = "#3E3D56",
  ["#CFE6FF"] = "#5F90F4", ["#7EAEFF"] = "#3A63DA", ["#4579EA"] = "#283DAB", ["#2F4FC7"] = "#1E2A7F",
  ["#22308F"] = "#17144F", ["#17144F"] = "#0E0C33",
})
PA.whiten(OUT .. "ore_top.aseprite", OUT .. "hit_flash", 0.72)

-- lapis lazuli drop: a chunky flake, light top-left, indigo rim, one pale streak
PA.sprite_from_grid(OUT .. "lapis_item", {
  "................",
  "................",
  ".......AAAA.....",
  ".....AAEFFEA....",
  "....AEFEEDDCA...",
  "...AEFEDDDDCBA..",
  "..AEEDDEEDCCBA..",
  "..AEDDDDEDCCBA..",
  ".AEDDCDDDDCCBA..",
  ".AEDDDCCDDCBBA..",
  ".ADDCDDDCCCBA...",
  ".ACDDDDCCCBBA...",
  "..ACDDCCCBBA....",
  "..ACCCBBBBA.....",
  "...AAAAAA.......",
  "................",
}, lapis)

-- pickaxe: Veinminer's diamond pickaxe (same author), plus a white flash and five glint-sweep frames
transform(VM .. "pickaxe_item.png", OUT .. "pickaxe_item", function(x, y, v) return v end)
PA.whiten(OUT .. "pickaxe_item.aseprite", OUT .. "pickaxe_flash", 0.8)
for i, c in ipairs({ -13, -8, -3, 2, 7 }) do glint(OUT .. "pickaxe_item.aseprite", OUT .. "pickaxe_glint_" .. (i - 1), c) end

-- open book, front view from slightly above: two pages in a V (outer corners high, spine low), uneven text lines,
-- red leather cover showing below and at the outer edges, a ribbon hanging from the spine.
-- 24x19 art padded to 24x24 (Blockbench animates textures taller than wide).
do
  local W, H = 24, 24
  local px = {}
  local function set(x, y, c) if x >= 0 and x < W and y >= 0 and y < H then px[PA.key(x, y)] = c end end
  local LINES = { { 3, 9 }, { 3, 7 }, { 3, 9 }, { 5, 9 }, { 3, 8 } }
  local function page(side)
    for i = 0, 10 do
      local x = side < 0 and 11 - i or 12 + i
      local t = i / 10
      local top = math.floor(5.5 - 4.2 * t + 0.5)
      local bot = math.floor(13.4 - 1.4 * t + 0.5)
      set(x, top - 1, book.a)
      for y = top, bot do set(x, y, i <= 1 and book.q or book.p) end
      for n, l in ipairs(LINES) do
        local y = top + 2 * n
        if y < bot - 1 and i >= l[1] and i <= l[2] then set(x, y, book.r) end
      end
      set(x, bot + 1, book.q)
      set(x, bot + 2, i >= 9 and book.d or book.c)
      set(x, bot + 3, book.b)
      set(x, bot + 4, book.a)
    end
    local ox = side < 0 and 0 or 23
    for y = 1, 17 do
      local inner = side < 0 and 1 or 22
      if px[PA.key(inner, y)] and px[PA.key(inner, y)] ~= book.a then set(ox, y, y > 12 and book.b or book.c) end
    end
    set(ox, 0, book.a); set(ox, 18, book.a)
    for y = 1, 17 do if not px[PA.key(ox, y)] then set(ox, y, book.a) end end
  end
  page(-1)
  page(1)
  for y = 5, 14 do set(11, y, book.r); set(12, y, book.q) end
  -- ribbon: gold tab at the spine, red tail hanging below the cover
  set(12, 15, book.h); set(12, 16, book.g); set(12, 17, book.d); set(12, 18, book.c); set(13, 19, book.b); set(12, 19, book.a); set(13, 18, book.a)
  set(11, 16, book.i); set(11, 17, book.a)
  PA.save_pixels(OUT .. "book_open", W, H, px)
end
for i, c in ipairs({ -16, -8, 0, 8, 16 }) do glint(OUT .. "book_open.aseprite", OUT .. "book_glint_" .. (i - 1), c) end

-- FX: Veinminer's star, puff, spark and ring; smear, spark and ring recoloured violet for the enchanted swing and charge-up
for _, n in ipairs({ "fx_star", "fx_puff", "fx_spark", "fx_ring" }) do
  transform(VM .. n .. ".png", OUT .. n, function(x, y, v) return v end)
end
PA.remap(VM .. "fx_smear.png", OUT .. "fx_smear", { ["#FFFFFF"] = "#FFFFFF", ["#B0FFF1"] = "#EDB8FF" })
PA.remap(VM .. "fx_spark.png", OUT .. "fx_spark_violet", { ["#FFFFFF"] = "#FFFFFF", ["#FFE14D"] = "#D77CFF", ["#FFF7BD"] = "#FBE0FF" })
PA.remap(VM .. "fx_ring.png", OUT .. "fx_ring_violet", { ["#FFFFFF"] = "#FFFFFF", ["#FFE14D"] = "#D77CFF" })

-- runes: four 6x6 glyphs with a violet outline, one per 8x8 quadrant of a 16x16 sheet
do
  local glyphs = {
    { "##..##", ".#..#.", "..##..", "..##..", "..##..", "..##.." },
    { "..##..", ".####.", "#.##.#", "..##..", "..##..", "..##.." },
    { "..##..", ".#..#.", "######", ".#..#.", "..##..", "......" },
    { "#.##.#", "#.##.#", "######", "..##..", "..##..", "..##.." },
  }
  local px = {}
  for gi, g in ipairs(glyphs) do
    local ox, oy = ((gi - 1) % 2) * 8 + 1, ((gi - 1) // 2) * 8 + 1
    local solid = {}
    for y = 1, 6 do for x = 1, 6 do
      if g[y]:sub(x, x) == "#" then solid[PA.key(ox + x - 1, oy + y - 1)] = true end
    end end
    for k in pairs(solid) do
      local x, y = k % 4096, k // 4096
      for dy = -1, 1 do for dx = -1, 1 do
        local n = PA.key(x + dx, y + dy)
        if not solid[n] then px[n] = "#7B1FB8" end
      end end
    end
    for k in pairs(solid) do px[k] = "#FCE4FF" end
  end
  PA.save_pixels(OUT .. "fx_rune", 16, 16, px)
end

-- debris tiles (4x4 each): light stone, lapis, dark stone
PA.sprite_from_grid(OUT .. "chunks", {
  "lllmEEDCmmmk....",
  "lmmkEDDCmkkN....",
  "lmmkDDCBmkkN....",
  "mkkkCBBAkNNN....",
  "................",
  "................",
  "................",
  "................",
  "................",
  "................",
  "................",
  "................",
  "................",
  "................",
  "................",
  "................",
}, merge(stone, lapis, { N = "#5A5A77" }))

-- backgrounds: flat teal plus a ground-shadow ellipse under the vein. Placed for the current crop (icon: 64px grid,
-- x8 = 512px; banner: 192x64 grid with ART_OFFSET in make_banner.py), so move them if the camera or crop changes.
local TEAL, SHADOW = "#1F5E63", "#16484C"
local function shadow_bg(path, w, h, cx, cy, a, b, extra)
  local px = {}
  for y = 0, h - 1 do
    for x = 0, w - 1 do
      local u, v = (x + 0.5 - cx) / a, (y + 0.5 - cy) / b
      px[PA.key(x, y)] = (u * u + v * v <= 1) and SHADOW or TEAL
    end
  end
  for k, c in pairs(extra or {}) do px[k] = c end
  PA.save_pixels(path, w, h, px)
end
shadow_bg(OUT .. "bg_flat", 64, 64, 24.0, 57.4, 18.3, 4.5)

-- banner: same shadow under the art, plus a few lighter-teal twinkles away from the text and the art
local tw = {}
local function twinkle(x, y, plus)
  tw[PA.key(x, y)] = plus and "#6CC3C2" or "#3E9A9E"
  if plus then for _, d in ipairs({ { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } }) do tw[PA.key(x + d[1], y + d[2])] = "#3E9A9E" end end
end
for _, t in ipairs({ { 4, 5, true }, { 100, 6 }, { 108, 55, true }, { 7, 58 }, { 184, 6, true }, { 186, 50 }, { 60, 58 }, { 179, 28 }, { 36, 4 } }) do
  twinkle(t[1], t[2], t[3])
end
shadow_bg(OUT .. "banner_bg", 192, 64, 144.6, 51.0, 15.0, 4.5, tw)

-- banner lettering: ENCHANTED in glint violet over VEINMINER in lapis blue; tagline white + violet accent
PA.title_sprite(OUT .. "banner_title_top", "ENCHANTED", {
  bands = { "#FCE4FF", "#F0B8FF", "#F0B8FF", "#F0B8FF", "#D77CFF", "#D77CFF", "#D77CFF", "#B04CE8", "#B04CE8", "#B04CE8" },
  extrude = { "#6A1FA8", "#3F0F6A" },
})
PA.title_sprite(OUT .. "banner_title", "VEINMINER", {
  bands = { "#E4F0FF", "#9CC2FF", "#9CC2FF", "#9CC2FF", "#6A98F4", "#6A98F4", "#6A98F4", "#4579EA", "#4579EA", "#4579EA" },
  extrude = { "#22308F", "#17144F" },
})
PA.label_sprite(OUT .. "banner_tagline", "ONE BOOK. WHOLE VEIN.", function(i) return i > 10 and "#E08CFF" or "#FFFFFF" end)
