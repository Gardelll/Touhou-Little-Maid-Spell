import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const sourcePath = process.argv[2]
    ?? "E:/Documents/Tencent Files/2868618204/FileRecv/投枪.bbmodel";
const sourceOutput = path.join(repositoryRoot, "model_sources/winefox_spear_projectile.bbmodel");
const geoOutput = path.join(repositoryRoot,
    "src/main/resources/assets/touhou_little_maid_spell/geo/winefox_spear_projectile.geo.json");
const textureOutput = path.join(repositoryRoot,
    "src/main/resources/assets/touhou_little_maid_spell/textures/entity/winefox_spear_projectile.png");
const reportOutput = path.join(repositoryRoot,
    "model_sources/winefox_spear_projectile.conversion-report.json");

function vector(value, fallback = [0, 0, 0]) {
    return (Array.isArray(value) ? value : fallback).slice(0, 3)
        .map(component => Number(component ?? 0));
}

function rounded(value) {
    return Number(Number(value).toFixed(5));
}

function convertCube(element) {
    const from = vector(element.from);
    const to = vector(element.to);
    const cube = {
        origin: [rounded(-to[0]), rounded(from[1]), rounded(from[2])],
        size: [rounded(to[0] - from[0]), rounded(to[1] - from[1]), rounded(to[2] - from[2])],
        uv: {}
    };
    const rotation = vector(element.rotation);
    if (rotation.some(component => component !== 0)) {
        const pivot = vector(element.origin);
        cube.pivot = [rounded(-pivot[0]), rounded(pivot[1]), rounded(pivot[2])];
        cube.rotation = [rounded(-rotation[0]), rounded(-rotation[1]), rounded(rotation[2])];
    }
    if (element.inflate !== undefined && Number(element.inflate) !== 0) {
        cube.inflate = rounded(element.inflate);
    }
    for (const [faceName, face] of Object.entries(element.faces ?? {})) {
        const uv = Array.isArray(face.uv) ? face.uv.map(Number) : [0, 0, 0, 0];
        cube.uv[faceName] = {
            uv: [rounded(Math.min(uv[0], uv[2])), rounded(Math.min(uv[1], uv[3]))],
            uv_size: [rounded(Math.abs(uv[2] - uv[0])), rounded(Math.abs(uv[3] - uv[1]))]
        };
    }
    return cube;
}

function convertGeometry(source) {
    const groups = new Map((source.groups ?? []).map(group => [group.uuid, group]));
    const elements = new Map((source.elements ?? []).map(element => [element.uuid, element]));
    const bones = [];
    const seenGroups = new Set();
    const seenElements = new Set();

    function visit(node, parentName) {
        const uuid = typeof node === "string" ? node : node?.uuid;
        const group = groups.get(uuid);
        if (!group || seenGroups.has(uuid)) {
            throw new Error(`Invalid or duplicate group ${uuid}`);
        }
        seenGroups.add(uuid);
        const pivot = vector(group.origin);
        const rotation = vector(group.rotation);
        const bone = {
            name: group.name,
            ...(parentName ? { parent: parentName } : {}),
            pivot: [rounded(-pivot[0]), rounded(pivot[1]), rounded(pivot[2])]
        };
        if (rotation.some(component => component !== 0)) {
            bone.rotation = [rounded(-rotation[0]), rounded(-rotation[1]), rounded(rotation[2])];
        }
        const childGroups = [];
        const cubes = [];
        for (const child of (typeof node === "object" ? node.children : undefined) ?? []) {
            const childUuid = typeof child === "string" ? child : child?.uuid;
            const element = elements.get(childUuid);
            if (element) {
                if (seenElements.has(childUuid)) {
                    throw new Error(`Duplicate element ${childUuid}`);
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
        childGroups.forEach(child => visit(child, group.name));
    }

    (source.outliner ?? []).forEach(root => visit(root, null));
    if (seenGroups.size !== groups.size || seenElements.size !== elements.size) {
        throw new Error(`Outliner coverage mismatch: groups ${seenGroups.size}/${groups.size}, `
            + `elements ${seenElements.size}/${elements.size}`);
    }
    return {
        format_version: "1.12.0",
        "minecraft:geometry": [{
            description: {
                identifier: "geometry.winefox_spear_projectile",
                texture_width: source.resolution?.width ?? 64,
                texture_height: source.resolution?.height ?? 64,
                visible_bounds_width: source.visible_box?.[0] ?? 8,
                visible_bounds_height: source.visible_box?.[1] ?? 3.5,
                visible_bounds_offset: [0, source.visible_box?.[2] ?? 1.25, 0]
            },
            bones
        }]
    };
}

function embeddedTexture(source) {
    const encoded = source.textures?.[0]?.source;
    const match = typeof encoded === "string" && encoded.match(/^data:image\/png;base64,(.+)$/s);
    if (!match) {
        throw new Error("Spear model has no embedded PNG texture");
    }
    return Buffer.from(match[1], "base64");
}

const sourceBuffer = fs.readFileSync(sourcePath);
const source = JSON.parse(sourceBuffer.toString("utf8"));
const geometry = convertGeometry(source);
const texture = embeddedTexture(source);
const standardizedSource = structuredClone(source);
standardizedSource.name = "winefox_spear_projectile";
standardizedSource.model_identifier = "winefox_spear_projectile";
for (const textureData of standardizedSource.textures ?? []) {
    textureData.name = "winefox_spear_projectile.png";
    textureData.path = "";
    textureData.relative_path = "winefox_spear_projectile.png";
}

for (const output of [sourceOutput, geoOutput, textureOutput, reportOutput]) {
    fs.mkdirSync(path.dirname(output), { recursive: true });
}
fs.writeFileSync(sourceOutput, JSON.stringify(standardizedSource));
fs.writeFileSync(geoOutput, JSON.stringify(geometry));
fs.writeFileSync(textureOutput, texture);

const bones = geometry["minecraft:geometry"][0].bones;
const report = {
    source: sourcePath,
    generatedAt: new Date().toISOString(),
    sourceSha256: crypto.createHash("sha256").update(sourceBuffer).digest("hex"),
    textureSha256: crypto.createHash("sha256").update(texture).digest("hex"),
    bones: bones.length,
    cubes: bones.reduce((total, bone) => total + (bone.cubes?.length ?? 0), 0),
    textureBytes: texture.length,
    rootBone: bones[0]?.name,
    rootRotation: bones[0]?.rotation
};
fs.writeFileSync(reportOutput, JSON.stringify(report, null, 2));
console.log(JSON.stringify(report, null, 2));
