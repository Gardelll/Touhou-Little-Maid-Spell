import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";

const DEFAULT_SOURCE = "E:\\Documents\\Tencent Files\\2868618204\\FileRecv\\magical_winefox_boss (1).bbmodel";
const DEFAULT_YSM_PACK = "E:\\Documents\\Tencent Files\\2868618204\\FileRecv\\\u4e07\u6cd5\u9152\u72d0-YSM\u72481.0.zip";
const DEFAULT_REFERENCE_GEO = "E:\\Documents\\Tencent Files\\2868618204\\FileRecv\\magical_winefox_boss.geo.json";
const DEFAULT_REFERENCE_ANIMATION = "E:\\Documents\\Tencent Files\\2868618204\\FileRecv\\magical_winefox_boss.animation.json";
const ROOT = path.resolve(import.meta.dirname, "..");
const sourcePath = path.resolve(process.argv[2] ?? DEFAULT_SOURCE);
const ysmPackPath = path.resolve(process.argv[3] ?? DEFAULT_YSM_PACK);
const referenceGeoPath = path.resolve(process.argv[4] ?? DEFAULT_REFERENCE_GEO);
const referenceAnimationPath = path.resolve(process.argv[5] ?? DEFAULT_REFERENCE_ANIMATION);

const sourceOutput = path.join(ROOT, "model_sources", "magical_winefox_boss.standard.bbmodel");
const geoOutput = path.join(ROOT, "src", "main", "resources", "assets", "touhou_little_maid_spell",
    "geo", "magical_winefox_boss.geo.json");
const animationOutput = path.join(ROOT, "src", "main", "resources", "assets", "touhou_little_maid_spell",
    "animations", "magical_winefox_boss.animation.json");
const textureOutput = path.join(ROOT, "src", "main", "resources", "assets", "touhou_little_maid_spell",
    "textures", "entity", "magical_winefox_boss.png");
const glowOutput = path.join(ROOT, "src", "main", "resources", "assets", "touhou_little_maid_spell",
    "textures", "entity", "magical_winefox_boss_glowmask.png");
const soundDirectory = path.join(ROOT, "src", "main", "resources", "assets", "touhou_little_maid_spell",
    "sounds", "entity", "magical_winefox_boss");
const reportOutput = path.join(ROOT, "model_sources", "magical_winefox_boss.conversion-report.json");

const ANIMATIONS = [
    ["animations/main.animation.json", "idle", "idle"],
    ["animations/main.animation.json", "walk", "walk"],
    ["animations/main.animation.json", "run", "run"],
    ["animations/main.animation.json", "fly", "fly"],
    ["animations/main.animation.json", "jump", "jump"],
    ["animations/main.animation.json", "sit", "sit"],
    ["animations/main.animation.json", "death", "death"],
    ["animations/main.animation.json", "attacked", "attacked"],
    ["animations/main.animation.json", "pre_parallel0", "ambient_parts"],
    ["animations/main.animation.json", "pre_parallel1", "blink"],
    ["animations/main.animation.json", "parallel0", "magic_rings"],
    ["animations/main.animation.json", "pre_parallel3", "tail_idle"],

    ["animations/arm.animation.json", "\u6756\u6b66\u5668\u5f62\u6001", "staff_form"],
    ["animations/arm.animation.json", "\u5251\u6b66\u5668\u5f62\u6001", "sword_form"],
    ["animations/arm.animation.json", "\u67aa\u6b66\u5668\u5f62\u6001", "spear_form"],
    ["animations/arm.animation.json", "\u6756\u5f62\u6001\u5f85\u673a", "phase_one_idle"],
    ["animations/arm.animation.json", "\u5251\u5f62\u6001\u5f85\u673a", "phase_two_idle"],
    ["animations/arm.animation.json", "hold_mainhand:bow", "hold_mainhand:bow"],
    ["animations/arm.animation.json", "hold_mainhand:sword", "hold_mainhand:sword"],
    ["animations/arm.animation.json", "use_mainhand:bow", "use_mainhand:bow"],
    ["animations/arm.animation.json", "swing:bow", "staff_attack_1"],
    ["animations/arm.animation.json", "swing:bow_2", "staff_attack_2"],
    ["animations/arm.animation.json", "sword_attack_01", "sword_attack_01"],
    ["animations/arm.animation.json", "sword_attack_02", "sword_attack_02"],
    ["animations/arm.animation.json", "sword_attack_03", "sword_attack_03"],
    ["animations/arm.animation.json", "sword_attack_04", "sword_attack_04"],
    ["animations/arm.animation.json", "\u8f6c\u9636\u6bb5\u52a8\u753b\uff08\u534a\u8840\u89e6\u53d1\uff0c\u8fc7\u7a0b\u65e0\u654c\uff0c\u6756\u8f6c\u5251\uff09", "phase_transition"],
    ["animations/arm.animation.json", "\u6295\u67aa\uff08\u7b2c\u4e8c\u79d2\u6295\u51fa\uff09", "spear_throw"],
    ["animations/extra.animation.json", "extra3", "defeat"]
];

const SOUND_NAMES = [
    "atk1", "atk2", "atk3", "atk3ready", "atked", "magic01", "magic01_shoot", "magicbow", "shengyin"
];

// The updated Blockbench file keeps the runtime-facing animation names for the
// phase-two actions, while a few legacy controller names still need aliases.
const DIRECT_ANIMATION_ALIASES = [
    ["idle", "idle"], ["walk", "walk"], ["run", "run"], ["fly", "fly"],
    ["jump", "jump"], ["sit", "sit"], ["death", "death"], ["attacked", "attacked"],
    ["pre_parallel0", "ambient_parts"], ["pre_parallel1", "blink"],
    ["parallel0", "magic_rings"], ["pre_parallel3", "tail_idle"],
    ["hold_mainhand:bow", "staff_form"], ["hold_mainhand:sword", "sword_form"],
    ["hold_mainhand:bow", "spear_form"], ["\u4e00\u9636\u6bb5\u5f85\u673a\u52a8\u753b", "phase_one_idle"],
    ["\u4e8c\u9636\u6bb5\u5f85\u673a\u52a8\u753b", "phase_two_idle"], ["hold_mainhand:bow", "hold_mainhand:bow"],
    ["hold_mainhand:sword", "hold_mainhand:sword"], ["hold_mainhand:bow", "use_mainhand:bow"],
    ["sword_attack_01", "staff_attack_1"], ["sword_attack_02", "staff_attack_2"],
    ["sword_attack_01", "sword_attack_01"], ["sword_attack_02", "sword_attack_02"],
    ["sword_attack_03", "sword_attack_03"], ["sword_attack_04", "sword_attack_04"],
    ["phase_transition", "phase_transition"], ["\u6295\u67aa\uff08\u7b2c\u4e8c\u79d2\u6295\u51fa\uff09", "spear_throw"],
    ["death", "defeat"]
];

function readZipEntries(zipPath) {
    const data = fs.readFileSync(zipPath);
    let eocd = -1;
    for (let offset = data.length - 22; offset >= Math.max(0, data.length - 0xffff - 22); offset--) {
        if (data.readUInt32LE(offset) === 0x06054b50) {
            eocd = offset;
            break;
        }
    }
    if (eocd < 0) {
        throw new Error(`Invalid ZIP archive: ${zipPath}`);
    }

    const entryCount = data.readUInt16LE(eocd + 10);
    let centralOffset = data.readUInt32LE(eocd + 16);
    const entries = new Map();
    for (let index = 0; index < entryCount; index++) {
        if (data.readUInt32LE(centralOffset) !== 0x02014b50) {
            throw new Error(`Invalid ZIP central directory at ${centralOffset}`);
        }
        const method = data.readUInt16LE(centralOffset + 10);
        const compressedSize = data.readUInt32LE(centralOffset + 20);
        const fileNameLength = data.readUInt16LE(centralOffset + 28);
        const extraLength = data.readUInt16LE(centralOffset + 30);
        const commentLength = data.readUInt16LE(centralOffset + 32);
        const localOffset = data.readUInt32LE(centralOffset + 42);
        const name = data.subarray(centralOffset + 46, centralOffset + 46 + fileNameLength).toString("utf8");

        if (data.readUInt32LE(localOffset) !== 0x04034b50) {
            throw new Error(`Invalid ZIP local header for ${name}`);
        }
        const localNameLength = data.readUInt16LE(localOffset + 26);
        const localExtraLength = data.readUInt16LE(localOffset + 28);
        const payloadOffset = localOffset + 30 + localNameLength + localExtraLength;
        const compressed = data.subarray(payloadOffset, payloadOffset + compressedSize);
        let payload;
        if (method === 0) {
            payload = Buffer.from(compressed);
        } else if (method === 8) {
            payload = zlib.inflateRawSync(compressed);
        } else {
            throw new Error(`Unsupported ZIP compression method ${method} for ${name}`);
        }
        entries.set(name.replaceAll("\\", "/"), payload);
        centralOffset += 46 + fileNameLength + extraLength + commentLength;
    }
    return entries;
}

function requiredEntry(entries, name) {
    const entry = entries.get(name);
    if (!entry) {
        throw new Error(`Missing ${name} in ${ysmPackPath}`);
    }
    return entry;
}

function parseJsonEntry(entries, name) {
    return JSON.parse(requiredEntry(entries, name).toString("utf8"));
}

function splitArguments(text) {
    const args = [];
    let start = 0;
    let depth = 0;
    let quote = null;
    for (let index = 0; index < text.length; index++) {
        const char = text[index];
        if (quote) {
            if (char === quote && text[index - 1] !== "\\") {
                quote = null;
            }
        } else if (char === "'" || char === '"') {
            quote = char;
        } else if (char === "(") {
            depth++;
        } else if (char === ")") {
            depth--;
        } else if (char === "," && depth === 0) {
            args.push(text.slice(start, index).trim());
            start = index + 1;
        }
    }
    args.push(text.slice(start).trim());
    return args;
}

function replaceSecondOrder(expression) {
    const functionName = "ysm.second_order";
    let output = expression;
    let index = output.indexOf(`${functionName}(`);
    while (index >= 0) {
        const open = index + functionName.length;
        let depth = 0;
        let quote = null;
        let end = open;
        for (; end < output.length; end++) {
            const char = output[end];
            if (quote) {
                if (char === quote && output[end - 1] !== "\\") {
                    quote = null;
                }
            } else if (char === "'" || char === '"') {
                quote = char;
            } else if (char === "(") {
                depth++;
            } else if (char === ")" && --depth === 0) {
                end++;
                break;
            }
        }
        const args = splitArguments(output.slice(open + 1, end - 1));
        const key = args[0]?.replace(/^['"]|['"]$/g, "");
        if (key !== "\u9798\u7fc5yaw") {
            throw new Error(`Unsupported ysm.second_order key ${JSON.stringify(key)} in ${expression}`);
        }
        output = `${output.slice(0, index)}variable.winefox_body_yaw${output.slice(end)}`;
        index = output.indexOf(`${functionName}(`, index + 1);
    }
    return output;
}

function unwrapDoubleNegatedProduct(expression) {
    // GeckoLib 4.7's Molang parser rejects `-(-(a) * b)`, even though the
    // equivalent `a * b` is valid. YSM emits this shape for the live head and
    // clothing pose in spell animations.
    if (!expression.startsWith("-(-") || !expression.endsWith(")")) {
        return expression;
    }

    const inner = expression.slice(3, -1);
    let depth = 0;
    let productIndex = -1;
    for (let index = 0; index < inner.length; index++) {
        const char = inner[index];
        if (char === "(") {
            depth++;
        } else if (char === ")") {
            depth--;
        } else if (char === "*" && depth === 0) {
            productIndex = index;
            break;
        }
    }

    if (productIndex < 0) {
        return expression;
    }

    const left = inner.slice(0, productIndex).trim();
    const right = inner.slice(productIndex + 1).trim();
    if (!left.startsWith("(") || !left.endsWith(")")) {
        return expression;
    }

    return `${left.slice(1, -1)}*${right}`;
}

function standardizeExpression(raw) {
    let expression = String(raw).trim().replace(/;$/, "");
    expression = replaceSecondOrder(expression);
    expression = expression
        .replace(/ysm\.input_vertical/gi, "variable.winefox_input_vertical")
        .replace(/ysm\.head_yaw/gi, "variable.winefox_head_yaw")
        .replace(/ysm\.head_pitch/gi, "variable.winefox_head_pitch")
        .replace(/ysm\.has_helmet/gi, "variable.winefox_has_helmet")
        .replace(/ysm\.has_mainhand/gi, "variable.winefox_has_mainhand")
        .replace(/ysm\.has_offhand/gi, "variable.winefox_has_offhand")
        .replace(/(?:v|variable)\.roaming\.[bc]/gi, "0")
        .replace(/q\.is_jumping/gi, "0")
        .replace(/(?:v|variable)\.random/gi, "0")
        .replace(/(?:v|variable)\.(?:f|s|swing_sword)/gi, "0");

    if (/(?:v|variable)\.(?:qh2?|jump)\b/i.test(expression)) {
        expression = "0";
    }

    const input = "variable.winefox_input_vertical";
    const movingBackward = `math.clamp((-${input} - 0.05) * 1000, 0, 1)`;
    expression = expression
        .replace(new RegExp(`\\(${input}\\s*<\\s*-0\\.05\\s*\\?\\s*-1\\s*:\\s*0\\)`, "g"), `(-1 * ${movingBackward})`)
        .replace(new RegExp(`\\(${input}\\s*<\\s*-0\\.05\\s*\\?\\s*0\\s*:\\s*-1\\)`, "g"), `((${movingBackward}) - 1)`)
        .replace(new RegExp(`\\(${input}\\s*<\\s*-0\\.05\\s*\\?\\s*0\\s*:\\s*1\\)`, "g"), `(1 - (${movingBackward}))`)
        .replace(new RegExp(`\\(${input}\\s*<\\s*-0\\.05\\s*\\?\\s*-1\\s*:\\s*1\\)`, "g"), `(1 - 2 * (${movingBackward}))`)
        .replace(new RegExp(`\\(${input}\\s*<\\s*-0\\.05\\s*\\?\\s*1\\s*:\\s*-1\\)`, "g"), `(2 * (${movingBackward}) - 1)`)
        .replace(new RegExp(`\\(${input}\\s*<\\s*-0\\.05\\s*\\?\\s*(-?\\d+(?:\\.\\d+)?)\\s*:\\s*(-?\\d+(?:\\.\\d+)?)\\)`, "g"),
            (match, whenTrue, whenFalse) => {
                const delta = Number(whenTrue) - Number(whenFalse);
                return `((${movingBackward}) * ${delta} + ${Number(whenFalse)})`;
            })
        .replace(/variable\.winefox_has_mainhand\s*\?\s*-50\s*:\s*-80/g,
            "(-80 + 30 * variable.winefox_has_mainhand)")
        .replace(/variable\.winefox_has_offhand\s*\?\s*-50\s*:\s*-80/g,
            "(-80 + 30 * variable.winefox_has_offhand)")
        .replace(/variable\.winefox_has_mainhand\s*\?\s*30\s*:\s*0/g,
            "(30 * variable.winefox_has_mainhand)")
        .replace(/variable\.winefox_has_offhand\s*\?\s*30\s*:\s*0/g,
            "(30 * variable.winefox_has_offhand)")
        .replace(/-variable\.winefox_has_(mainhand|offhand)\s*\?\s*(-?\d+(?:\.\d+)?)\s*:\s*(-?\d+(?:\.\d+)?)/g,
            (match, hand, whenTrue, whenFalse) => {
                const delta = Number(whenTrue) - Number(whenFalse);
                return `(-1 * (variable.winefox_has_${hand} * ${delta} + ${Number(whenFalse)}))`;
            })
        .replace(/\(variable\.winefox_head_yaw\s*<=\s*0\s*\?\s*-variable\.winefox_head_yaw\)/g,
            "-math.min(variable.winefox_head_yaw, 0)")
        .replace(/\(variable\.winefox_head_yaw\s*<\s*0\s*\?\s*-0\.5\s*\*\s*variable\.winefox_head_yaw\)/g,
            "(-0.5 * math.min(variable.winefox_head_yaw, 0))")
        .replace(/\(variable\.winefox_head_yaw\s*>\s*0\s*\?\s*-variable\.winefox_head_yaw\)/g,
            "-math.max(variable.winefox_head_yaw, 0)")
        .replace(/(-?\d+(?:\.\d+)?)\s*==\s*(-?\d+(?:\.\d+)?)\s*\?\s*(-?\d+(?:\.\d+)?)\s*:\s*(-?\d+(?:\.\d+)?)/g,
            (match, left, right, whenTrue, whenFalse) => Number(left) === Number(right) ? whenTrue : whenFalse)
        .replace(
            /query\.vertical_speed\s*\/\s*math\.abs\(query\.vertical_speed\)/g,
            "math.clamp(query.vertical_speed * 1000000, -1, 1)");

    expression = unwrapDoubleNegatedProduct(expression);

    if (/[?]|==|!=|&&|\|\||ysm\./i.test(expression)) {
        throw new Error(`Unsupported GeckoLib Molang expression after conversion: ${expression}`);
    }
    return /^[-+]?\d+(?:\.\d+)?$/.test(expression) ? Number(expression) : expression;
}

function transformBoneData(value) {
    if (Array.isArray(value)) {
        return value.map(transformBoneData);
    }
    if (value && typeof value === "object") {
        return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, transformBoneData(item)]));
    }
    if (typeof value === "string") {
        return standardizeExpression(value);
    }
    return value;
}

function standardizeAnimation(animation) {
    const output = structuredClone(animation);
    if (output.bones) {
        output.bones = transformBoneData(output.bones);
    }
    return output;
}

function crc32(buffer) {
    let crc = 0xffffffff;
    for (const byte of buffer) {
        crc ^= byte;
        for (let index = 0; index < 8; index++) {
            crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1));
        }
    }
    return (crc ^ 0xffffffff) >>> 0;
}

function pngChunk(type, data) {
    const typeBuffer = Buffer.from(type, "ascii");
    const length = Buffer.alloc(4);
    length.writeUInt32BE(data.length);
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(crc32(Buffer.concat([typeBuffer, data])));
    return Buffer.concat([length, typeBuffer, data, crc]);
}

function decodePng(buffer) {
    if (buffer.subarray(0, 8).toString("hex") !== "89504e470d0a1a0a") {
        throw new Error("Texture is not a PNG");
    }
    let offset = 8;
    let width;
    let height;
    let bitDepth;
    let colorType;
    const idat = [];
    while (offset < buffer.length) {
        const length = buffer.readUInt32BE(offset);
        const type = buffer.subarray(offset + 4, offset + 8).toString("ascii");
        const chunk = buffer.subarray(offset + 8, offset + 8 + length);
        offset += length + 12;
        if (type === "IHDR") {
            width = chunk.readUInt32BE(0);
            height = chunk.readUInt32BE(4);
            bitDepth = chunk[8];
            colorType = chunk[9];
            if (chunk[12] !== 0) {
                throw new Error("Interlaced PNG textures are not supported");
            }
        } else if (type === "IDAT") {
            idat.push(chunk);
        } else if (type === "IEND") {
            break;
        }
    }
    if (bitDepth !== 8 || ![2, 6].includes(colorType)) {
        throw new Error(`Unsupported PNG format: bitDepth=${bitDepth}, colorType=${colorType}`);
    }
    const channels = colorType === 6 ? 4 : 3;
    const stride = width * channels;
    const filtered = zlib.inflateSync(Buffer.concat(idat));
    const raw = Buffer.alloc(height * stride);
    let sourceOffset = 0;
    for (let y = 0; y < height; y++) {
        const filter = filtered[sourceOffset++];
        for (let x = 0; x < stride; x++) {
            const value = filtered[sourceOffset++];
            const left = x >= channels ? raw[y * stride + x - channels] : 0;
            const up = y > 0 ? raw[(y - 1) * stride + x] : 0;
            const upperLeft = y > 0 && x >= channels ? raw[(y - 1) * stride + x - channels] : 0;
            let decoded;
            if (filter === 0) decoded = value;
            else if (filter === 1) decoded = value + left;
            else if (filter === 2) decoded = value + up;
            else if (filter === 3) decoded = value + Math.floor((left + up) / 2);
            else if (filter === 4) {
                const predictor = left + up - upperLeft;
                const pa = Math.abs(predictor - left);
                const pb = Math.abs(predictor - up);
                const pc = Math.abs(predictor - upperLeft);
                decoded = value + (pa <= pb && pa <= pc ? left : pb <= pc ? up : upperLeft);
            } else {
                throw new Error(`Unsupported PNG filter ${filter}`);
            }
            raw[y * stride + x] = decoded & 0xff;
        }
    }
    const rgba = Buffer.alloc(width * height * 4);
    for (let pixel = 0; pixel < width * height; pixel++) {
        rgba[pixel * 4] = raw[pixel * channels];
        rgba[pixel * 4 + 1] = raw[pixel * channels + 1];
        rgba[pixel * 4 + 2] = raw[pixel * channels + 2];
        rgba[pixel * 4 + 3] = channels === 4 ? raw[pixel * channels + 3] : 255;
    }
    return { width, height, rgba };
}

function encodePng(width, height, rgba) {
    const ihdr = Buffer.alloc(13);
    ihdr.writeUInt32BE(width, 0);
    ihdr.writeUInt32BE(height, 4);
    ihdr[8] = 8;
    ihdr[9] = 6;
    const scanlines = Buffer.alloc(height * (1 + width * 4));
    for (let y = 0; y < height; y++) {
        const offset = y * (1 + width * 4);
        scanlines[offset] = 0;
        rgba.copy(scanlines, offset + 1, y * width * 4, (y + 1) * width * 4);
    }
    return Buffer.concat([
        Buffer.from("89504e470d0a1a0a", "hex"),
        pngChunk("IHDR", ihdr),
        pngChunk("IDAT", zlib.deflateSync(scanlines, { level: 9 })),
        pngChunk("IEND", Buffer.alloc(0))
    ]);
}

function addFacePixels(target, face, width, height) {
    if (!face?.uv || !face?.uv_size) {
        return;
    }
    const [u, v] = face.uv.map(Number);
    const [du, dv] = face.uv_size.map(Number);
    const minU = Math.max(0, Math.floor(Math.min(u, u + du)));
    const maxU = Math.min(width, Math.ceil(Math.max(u, u + du)));
    const minV = Math.max(0, Math.floor(Math.min(v, v + dv)));
    const maxV = Math.min(height, Math.ceil(Math.max(v, v + dv)));
    for (let y = minV; y < maxV; y++) {
        for (let x = minU; x < maxU; x++) {
            target.add(y * width + x);
        }
    }
}

function buildGlowMask(textureBuffer, bones) {
    const image = decodePng(textureBuffer);
    const glowingPixels = new Set();
    const normalPixels = new Set();
    for (const bone of bones) {
        const target = bone.name.startsWith("ysmGlow") ? glowingPixels : normalPixels;
        for (const cube of bone.cubes ?? []) {
            if (Array.isArray(cube.uv)) {
                throw new Error(`Box UV is not supported for glow-mask generation on ${bone.name}`);
            }
            for (const face of Object.values(cube.uv ?? {})) {
                addFacePixels(target, face, image.width, image.height);
            }
        }
    }
    const mask = Buffer.alloc(image.rgba.length);
    for (const pixel of glowingPixels) {
        const offset = pixel * 4;
        image.rgba.copy(mask, offset, offset, offset + 4);
    }
    return {
        png: encodePng(image.width, image.height, mask),
        glowingPixelCount: glowingPixels.size,
        sharedPixelCount: [...glowingPixels].filter(pixel => normalPixels.has(pixel)).length
    };
}

function collectExpressionStrings(value, output = []) {
    if (Array.isArray(value)) {
        for (const item of value) collectExpressionStrings(item, output);
    } else if (value && typeof value === "object") {
        for (const item of Object.values(value)) collectExpressionStrings(item, output);
    } else if (typeof value === "string" && Number.isNaN(Number(value))) {
        output.push(value);
    }
    return output;
}

function sha256(buffer) {
    return crypto.createHash("sha256").update(buffer).digest("hex");
}

function numericVector(value, fallback = [0, 0, 0]) {
    const vector = Array.isArray(value) ? value : fallback;
    return vector.slice(0, 3).map(component => Number(component ?? 0));
}

function rounded(value) {
    return Number(Number(value).toFixed(5));
}

function convertCube(element) {
    const from = numericVector(element.from);
    const to = numericVector(element.to);
    const cube = {
        origin: [rounded(-to[0]), rounded(from[1]), rounded(from[2])],
        size: [rounded(to[0] - from[0]), rounded(to[1] - from[1]), rounded(to[2] - from[2])],
        uv: {}
    };
    const rotation = numericVector(element.rotation);
    if (rotation.some(component => component !== 0)) {
        const pivot = numericVector(element.origin);
        cube.pivot = [rounded(-pivot[0]), rounded(pivot[1]), rounded(pivot[2])];
        cube.rotation = [rounded(-rotation[0]), rounded(-rotation[1]), rounded(rotation[2])];
    }
    if (element.inflate !== undefined && Number(element.inflate) !== 0) {
        cube.inflate = rounded(element.inflate);
    }
    for (const [faceName, face] of Object.entries(element.faces ?? {})) {
        const uv = Array.isArray(face.uv) ? face.uv.map(value => Number(value)) : [0, 0, 0, 0];
        cube.uv[faceName] = {
            uv: [rounded(Math.min(uv[0], uv[2])), rounded(Math.min(uv[1], uv[3]))],
            uv_size: [rounded(Math.abs(uv[2] - uv[0])), rounded(Math.abs(uv[3] - uv[1]))]
        };
    }
    return cube;
}

function convertDirectGeometry(source) {
    const groups = new Map((source.groups ?? []).map(group => [group.uuid, group]));
    const elements = new Map((source.elements ?? []).map(element => [element.uuid, element]));
    const bones = [];
    const seenGroups = new Set();
    const seenElements = new Set();

    function visit(node, parentName) {
        const uuid = typeof node === "string" ? node : node?.uuid;
        const group = groups.get(uuid);
        if (!group) {
            throw new Error(`Outliner references unknown group ${uuid}`);
        }
        if (seenGroups.has(uuid)) {
            throw new Error(`Group ${uuid} appears more than once in the outliner`);
        }
        seenGroups.add(uuid);
        const pivot = numericVector(group.origin);
        const rotation = numericVector(group.rotation);
        const bone = {
            name: group.name,
            ...(parentName ? { parent: parentName } : {}),
            pivot: [-pivot[0], pivot[1], pivot[2]]
        };
        if (rotation.some(component => component !== 0)) {
            bone.rotation = [-rotation[0], -rotation[1], rotation[2]];
        }
        const cubes = [];
        const childGroups = [];
        for (const child of (typeof node === "object" ? node.children : undefined) ?? []) {
            const childUuid = typeof child === "string" ? child : child?.uuid;
            const element = elements.get(childUuid);
            if (element) {
                if (seenElements.has(childUuid)) {
                    throw new Error(`Element ${childUuid} appears more than once in the outliner`);
                }
                seenElements.add(childUuid);
                cubes.push(convertCube(element));
            } else {
                childGroups.push(child);
            }
        }
        if (cubes.length) {
            bone.cubes = cubes;
        }
        bones.push(bone);
        for (const child of childGroups) {
            visit(child, group.name);
        }
    }

    for (const root of source.outliner ?? []) {
        visit(root, null);
    }
    if (seenGroups.size !== groups.size || seenElements.size !== elements.size) {
        throw new Error(`Outliner coverage mismatch: groups ${seenGroups.size}/${groups.size}, elements ${seenElements.size}/${elements.size}`);
    }
    return {
        format_version: "1.12.0",
        "minecraft:geometry": [{
            description: {
                identifier: "geometry.magical_winefox_boss",
                texture_width: source.resolution?.width ?? 1024,
                texture_height: source.resolution?.height ?? 1024,
                visible_bounds_width: source.visible_box?.[0] ?? 19,
                visible_bounds_height: source.visible_box?.[1] ?? 19,
                visible_bounds_offset: [0, source.visible_box?.[2] ?? 1.5, 0]
            },
            bones
        }]
    };
}

function convertAnimationVector(dataPoint, channel) {
    const values = [dataPoint?.x ?? 0, dataPoint?.y ?? 0, dataPoint?.z ?? 0]
        .map(value => typeof value === "string" ? standardizeExpression(value) : Number(value));
    const negateAxes = channel === "position" ? [true, false, false]
        : channel === "rotation" ? [true, true, false]
            : [false, false, false];
    return values.map((value, index) => {
        if (!negateAxes[index]) {
            return value;
        }
        if (typeof value === "number") {
            return -value;
        }
        return unwrapDoubleNegatedProduct(`-(${value})`);
    });
}

function convertAnimationTrack(keyframes, channel) {
    const points = keyframes
        .filter(keyframe => keyframe.channel === channel)
        .sort((left, right) => left.time - right.time);
    if (!points.length) {
        return undefined;
    }
    if (points.length === 1 && points[0].time === 0) {
        return convertAnimationVector(points[0].data_points?.[0], channel);
    }
    const timeline = {};
    for (const keyframe of points) {
        const value = convertAnimationVector(keyframe.data_points?.[0], channel);
        timeline[String(keyframe.time)] = keyframe.interpolation === "linear"
            ? value
            : { post: value, lerp_mode: "catmullrom" };
    }
    return timeline;
}

function convertDirectAnimation(animation, groups) {
    const output = {};
    if (animation.loop === "loop") {
        output.loop = true;
    } else if (animation.loop === "hold") {
        output.loop = "hold";
    }
    if (Number(animation.length) > 0) {
        output.animation_length = Number(animation.length);
    }
    const bones = {};
    const soundEffects = {};
    for (const [groupUuid, animator] of Object.entries(animation.animators ?? {})) {
        const group = groups.get(groupUuid);
        if (!Array.isArray(animator.keyframes)) {
            continue;
        }
        if (!group && animator.type === "effect") {
            for (const keyframe of animator.keyframes) {
                if (keyframe.channel !== "sound") {
                    continue;
                }
                const effect = keyframe.data_points?.[0]?.effect;
                if (effect) {
                    soundEffects[String(keyframe.time)] = { effect };
                }
            }
            continue;
        }
        if (!group) {
            continue;
        }
        const tracks = {};
        for (const channel of ["position", "rotation", "scale"]) {
            const track = convertAnimationTrack(animator.keyframes, channel);
            if (track !== undefined) {
                tracks[channel] = track;
            }
        }
        if (Object.keys(tracks).length) {
            bones[group.name] = tracks;
        }
    }
    if (Object.keys(bones).length) {
        output.bones = bones;
    }
    if (Object.keys(soundEffects).length) {
        output.sound_effects = soundEffects;
    }
    return output;
}

function embeddedTexture(source) {
    const encoded = source.textures?.[0]?.source;
    const match = typeof encoded === "string" && encoded.match(/^data:image\/png;base64,(.+)$/s);
    if (!match) {
        throw new Error("Updated Blockbench source has no embedded PNG texture");
    }
    return Buffer.from(match[1], "base64");
}

const sourceBuffer = fs.readFileSync(sourcePath);
const source = JSON.parse(sourceBuffer.toString("utf8"));
const zipEntries = readZipEntries(ysmPackPath);
const hasReferenceExports = fs.existsSync(referenceGeoPath) && fs.existsSync(referenceAnimationPath);
const geometry = hasReferenceExports
    ? JSON.parse(fs.readFileSync(referenceGeoPath, "utf8"))
    : convertDirectGeometry(source);
const geometryData = geometry["minecraft:geometry"][0];
const sourceGroups = new Map((source.groups ?? []).map(group => [group.uuid, group]));
const sourceAnimations = new Map((source.animations ?? []).map(animation => [animation.name, animation]));
const convertedAnimations = hasReferenceExports
    ? Object.fromEntries(Object.entries(
        JSON.parse(fs.readFileSync(referenceAnimationPath, "utf8")).animations ?? {}
    ).map(([name, animation]) => [name, standardizeAnimation(animation)]))
    : {};

if (!hasReferenceExports) {
    for (const [sourceName, outputName] of DIRECT_ANIMATION_ALIASES) {
        const animation = sourceAnimations.get(sourceName);
        if (!animation) {
            throw new Error(`Missing animation ${sourceName} in updated Blockbench source`);
        }
        if (convertedAnimations[outputName]) {
            throw new Error(`Duplicate output animation name ${outputName}`);
        }
        convertedAnimations[outputName] = convertDirectAnimation(animation, sourceGroups);
    }
}

for (const [fileName, sourceName, outputName] of [
    ["animations/arm.animation.json", "swing:bow", "staff_attack_1"],
    ["animations/arm.animation.json", "swing:bow_2", "staff_attack_2"],
    ["animations/extra.animation.json", "extra3", "defeat"]
]) {
    if (hasReferenceExports && (outputName === "staff_attack_2" || convertedAnimations[outputName])) {
        continue;
    }
    const animation = parseJsonEntry(zipEntries, fileName).animations?.[sourceName];
    if (!animation) {
        throw new Error(`Missing legacy animation ${sourceName} in ${fileName}`);
    }
    convertedAnimations[outputName] = standardizeAnimation(animation);
}
if (hasReferenceExports && !convertedAnimations.staff_attack_2 && convertedAnimations.staff_attack_1) {
    convertedAnimations.staff_attack_2 = structuredClone(convertedAnimations.staff_attack_1);
}

// Always use the ISS clips from the updated Blockbench source. Reference exports
// may contain same-named clips from an older model hierarchy.
for (const animation of source.animations ?? []) {
    if (animation.name.startsWith("iss:")) {
        convertedAnimations[animation.name] = convertDirectAnimation(animation, sourceGroups);
    }
}

// The current 1.20.1 ISS build uses katana_upslash for Shadow Slash, while the
// supplied Winefox source only contains the older retargeted instant slash.
// Keep a named compatibility alias so the runtime can follow the spell's
// AnimationHolder without falling back to a staff/sword basic attack.
if (!convertedAnimations["iss:katana_upslash"] && convertedAnimations["iss:instant_slash"]) {
    convertedAnimations["iss:katana_upslash"] = structuredClone(convertedAnimations["iss:instant_slash"]);
}

// The ISS clips are authored for a different entity root and include a copy of
// the generic locomotion pose.  The action controller runs after the idle and
// tail controllers, so those tracks would override the Winefox's live body
// yaw, movement pitch, vertical bob, and tail-root orientation while casting.
// Keep the action-specific upper-body/weapon tracks and let the regular
// controllers own the shared base pose.
const ISS_BASE_POSE_BONES = new Set(["MRoot", "Root", "MAllbody", "AllBody", "UpBody", "MTail"]);
for (const [animationName, animation] of Object.entries(convertedAnimations)) {
    if (!animationName.startsWith("iss:") || !animation.bones) {
        continue;
    }
    for (const boneName of ISS_BASE_POSE_BONES) {
        delete animation.bones[boneName];
    }
    if (!Object.keys(animation.bones).length) {
        delete animation.bones;
    }
}

// Keep phase-two weapon visibility independent from hold-controller transitions.
if (!hasReferenceExports) {
    convertedAnimations.sword_form.bones ??= {};
    convertedAnimations.sword_form.bones.Mweapon = { scale: 1 };
}

const tailBoneNames = Object.keys(convertedAnimations.tail_idle.bones ?? {});
for (const [sourceName, outputName] of [
    ["walk", "tail_walk"],
    ["run", "tail_run"],
    ["jump", "tail_jump"]
]) {
    const sourceAnimation = convertedAnimations[sourceName];
    const bones = {};
    for (const boneName of tailBoneNames) {
        const boneAnimation = sourceAnimation.bones?.[boneName];
        if (!boneAnimation) {
            throw new Error(`Missing tail bone ${boneName} in ${sourceName}`);
        }
        bones[boneName] = structuredClone(boneAnimation);
    }
    if (!convertedAnimations[outputName]) {
        convertedAnimations[outputName] = {
            loop: sourceAnimation.loop ?? true,
            ...(sourceAnimation.animation_length === undefined
                ? {}
                : { animation_length: sourceAnimation.animation_length }),
            bones
        };
    }
}

const animations = { format_version: "1.8.0", animations: convertedAnimations };

const boneNames = new Set(geometryData.bones.map(bone => bone.name));
const missingAnimationBones = [];
for (const [animationName, animation] of Object.entries(convertedAnimations)) {
    for (const boneName of Object.keys(animation.bones ?? {})) {
        if (!boneNames.has(boneName)) {
            missingAnimationBones.push({ animation: animationName, bone: boneName });
            delete animation.bones[boneName];
        }
    }
    if (animation.bones && !Object.keys(animation.bones).length) {
        delete animation.bones;
    }
}

const texture = embeddedTexture(source);
const glowMask = buildGlowMask(texture, geometryData.bones);
const standardizedSource = structuredClone(source);
standardizedSource.name = "magical_winefox_boss_standard";
standardizedSource.model_identifier = "magical_winefox_boss";
for (const textureData of standardizedSource.textures ?? []) {
    textureData.name = "magical_winefox_boss.png";
    textureData.path = "";
    textureData.relative_path = "magical_winefox_boss.png";
}

const expressions = [];
for (const animation of Object.values(convertedAnimations)) {
    collectExpressionStrings(animation.bones, expressions);
}
const expressionText = expressions.join("\n");
const geometryText = JSON.stringify(geometry);
const outputDirectories = [sourceOutput, geoOutput, animationOutput, textureOutput, glowOutput, reportOutput, soundDirectory];
for (const output of outputDirectories) {
    fs.mkdirSync(path.extname(output) ? path.dirname(output) : output, { recursive: true });
}

fs.writeFileSync(sourceOutput, JSON.stringify(standardizedSource));
fs.writeFileSync(geoOutput, geometryText);
fs.writeFileSync(animationOutput, JSON.stringify(animations));
fs.writeFileSync(textureOutput, texture);
fs.writeFileSync(glowOutput, glowMask.png);
for (const soundName of SOUND_NAMES) {
    fs.writeFileSync(path.join(soundDirectory, `${soundName}.ogg`), requiredEntry(zipEntries, `sounds/${soundName}.ogg`));
}

const report = {
    source: sourcePath,
    ysmPack: ysmPackPath,
    referenceGeo: hasReferenceExports ? referenceGeoPath : null,
    referenceAnimation: hasReferenceExports ? referenceAnimationPath : null,
    usedReferenceExports: hasReferenceExports,
    generatedAt: new Date().toISOString(),
    hashes: {
        bbmodelSha256: sha256(sourceBuffer),
        ysmPackSha256: sha256(fs.readFileSync(ysmPackPath)),
        textureSha256: sha256(texture)
    },
    sourceStats: {
        groups: source.groups?.length ?? 0,
        elements: source.elements?.length ?? 0,
        animations: source.animations?.length ?? 0
    },
    outputStats: {
        bones: geometryData.bones.length,
        cubes: geometryData.bones.reduce((total, bone) => total + (bone.cubes?.length ?? 0), 0),
        animations: Object.keys(convertedAnimations).length,
        animationTracks: Object.values(convertedAnimations)
            .reduce((total, animation) => total + Object.keys(animation.bones ?? {}).length, 0),
        sounds: SOUND_NAMES.length
    },
    animationNames: Object.keys(convertedAnimations),
    missingAnimationBones,
    glowMask: {
        glowingPixels: glowMask.glowingPixelCount,
        pixelsAlsoUsedByNormalBones: glowMask.sharedPixelCount
    },
    assertions: {
        containsYsmExpression: /ysm\./i.test(expressionText),
        containsUnsupportedConditional: /[?]|==|!=|&&|\|\|/.test(expressionText),
        containsLegacyStateVariable: /(?:v|variable)\.(?:qh2?|jump|roaming|random|swing_sword)\b/i.test(expressionText),
        containsYsmGlowBones: /ysmGlow/.test(geometryText),
        sourceAndRuntimeBoneCountsMatch: source.groups?.length === geometryData.bones.length,
        sourceAndRuntimeCubeCountsMatch: source.elements?.length === geometryData.bones
            .reduce((total, bone) => total + (bone.cubes?.length ?? 0), 0)
    }
};

if (report.assertions.containsYsmExpression
        || report.assertions.containsUnsupportedConditional
        || report.assertions.containsLegacyStateVariable
        || !report.assertions.containsYsmGlowBones
        || !report.assertions.sourceAndRuntimeBoneCountsMatch
        || !report.assertions.sourceAndRuntimeCubeCountsMatch) {
    throw new Error(`Conversion assertions failed: ${JSON.stringify(report.assertions)}`);
}

fs.writeFileSync(reportOutput, JSON.stringify(report, null, 2));
console.log(JSON.stringify(report, null, 2));
