# 归隐之地转场重绘指南

转场以 `384 × 216` 的逻辑画布合成。游戏会按比例缩放并居中裁切，枝影、花瓣、光束等元素可以独立改色或重绘。

| PNG | 原始尺寸 | 画法 |
|---|---:|---|
| `petal_far.png`、`petal_middle.png`、`petal_near.png` | 24 × 20 | 飘落的单片花瓣，按远、中、近分别绘制。保留透明背景，图案居中。 |
| `blossom_far.png`、`blossom_middle.png`、`blossom_near.png` | 28 × 28 | 粒子中的整朵樱花，按远、中、近分别绘制。保留透明背景，图案居中。 |
| `blossom_emblem.png` | 28 × 28 | 遮屏期间短暂出现的中央徽记。适合轮廓清楚的居中图案。 |
| `branch.png` | 64 × 16 | 左上角枝影。同一张图会旋转 180° 后再绘制在右下角。 |
| `fog.png` | 384 × 216 | 全屏雾底，使用覆盖画布的实色纹理。游戏以颜色通道绘制，遮屏时保证全屏不透明。 |
| `light_rays.png` | 384 × 216 | 斜光层。用透明度控制光束边缘，再用颜色控制光色。 |
| `mist_specks.png` | 384 × 216 | 雾中亮点，使用透明背景。 |
| `reveal_mask.png` | 192 × 108 | 灰度揭幕顺序图。越暗的像素越早透出实景，越亮的越晚。雾底、斜光和雾点共用这张图。 |

## 在游戏里查看重绘效果

1. 把素材资源包放进客户端的 `resourcepacks` 目录，再从资源包菜单启用。资源包内的纹理位于 `assets/touhou_little_maid_spell/textures/gui/retreat_transition/`。
2. 编辑 PNG 后保留文件名和目录。可使用原始分辨率，也可按相同比例提高分辨率。透明图导出为 RGBA PNG，揭幕图导出为无透明度的灰度 PNG。
3. 保存后按 `F3 + T` 重载资源，在主世界右键使用寻风之铃，检查遮屏、揭幕和宽高比裁切。资源包可以仅包含本次替换的图层。
4. 美术定稿后，把对应 PNG 放入 `src/main/resources/assets/touhou_little_maid_spell/textures/gui/retreat_transition/`，即可打包为模组默认图层。
