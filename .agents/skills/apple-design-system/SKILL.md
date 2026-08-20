---
name: apple-design-system
description: UI/UX design craftsmanship principles inspired by Apple Human Interface Guidelines (HIG). Use when creating refined, premium interfaces featuring clean typography, translucent depth, subtle borders, high contrast, smooth curves, and micro-interactions.
---

# Apple Design System & Craftsmanship Skill

This skill captures key principles from the Apple Human Interface Guidelines (HIG) to create modern, polished, and delightful user experiences.

## 1. Core Principles

- **Clarity**: Text is legible at every size, icons are precise, adornments are subtle, and the focus remains on the user's content.
- **Deference**: Fluid motion and a crisp, clear interface help people understand and interact with content without ever competing with it.
- **Depth**: Distinct visual layers and realistic motion convey hierarchy, impart vitality, and facilitate understanding.

## 2. Visual Style & Surface Craft

- **Continuous Curves (Squircle)**: Use smooth rounded corners (`16.dp` to `24.dp`) for cards and grouped lists.
- **Subtle Borders & Hairlines**: Cards and dividers should use translucent border strokes (e.g. `Color.White.copy(alpha = 0.08f)` on dark, `Color.Black.copy(alpha = 0.06f)` on light) rather than heavy borders.
- **Layered Elevation & Translucency**:
  - Base background: Deep black or neutral slate.
  - Secondary surface: Elevated grouped containers (`#1C1C1E` dark, `#FFFFFF` light).
  - Tertiary surface: Inset items (`#2C2C2E` dark, `#F2F2F7` light).
- **Vibrant Accent Contrasts**: Clean system accents (Green, Orange, Purple, Blue) paired with high-contrast text.

## 3. Typography & Spacing
- Maintain clear typographic hierarchy with distinct font weights (Bold headers, Medium labels, Regular body).
- Use generous internal padding (`16.dp` to `20.dp`) inside cards to give data room to breathe.
- Group related metrics together in symmetrical grid tiles or segmented cards.

## 4. Micro-Interactions & Feedback
- Provide visual feedback on press/tap with subtle scale or background alpha transitions.
- Use smooth spring animations for state changes and sheet/dialog presentation.
