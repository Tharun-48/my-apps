#!/usr/bin/env node
/**
 * Universal MorphLLM Workspace Toolkit
 * General-purpose tool for ALL projects, workspaces, and CLI operations.
 * 
 * Commands:
 *   node scripts/morph_router.js route "<prompt>"      -> Classifies model (fast vs large)
 *   node scripts/morph_router.js compact "<text/file>"   -> Compacts logs/text (33k tok/s)
 *   node scripts/morph_router.js ask "<prompt>"         -> Completes query via Morph API
 *   node scripts/morph_router.js apply "<file>" "<diff>"-> Fast Apply simulation
 */

const fs = require('fs');

const MORPH_API_KEY = process.env.MORPH_API_KEY || "sk-iEhZOp87U7Bcr-k3QhfMR2mBewnB2M39BD-RGN3tKOVoIZDT";
const MORPH_BASE_URL = process.env.MORPH_BASE_URL || "https://api.morphllm.com/v1";

class MorphToolkit {
    constructor(apiKey = MORPH_API_KEY, baseUrl = MORPH_BASE_URL) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.replace(/\/$/, "");
    }

    classifyPrompt(prompt) {
        const length = prompt.length;
        const complexKeywords = ["refactor", "architecture", "debug", "optimize", "redesign", "security", "build"];
        const isComplex = length > 250 || complexKeywords.some(kw => prompt.toLowerCase().includes(kw));
        return isComplex ? "morph-glm52-744b" : "morph-v3-fast";
    }

    compactText(text, maxLines = 100) {
        let content = text;
        if (fs.existsSync(text)) {
            content = fs.readFileSync(text, 'utf-8');
        }
        const lines = content.split('\n');
        if (lines.length <= maxLines) return content;

        const seen = new Set();
        const deduped = [];
        for (const line of lines) {
            const trimmed = line.trim();
            if (trimmed && seen.has(trimmed)) continue;
            if (trimmed) seen.add(trimmed);
            deduped.push(line);
        }

        if (deduped.length > maxLines) {
            const half = Math.floor(maxLines / 2);
            const head = deduped.slice(0, half);
            const tail = deduped.slice(deduped.length - half);
            return head.join('\n') + `\n\n... [Morph Compact: ${deduped.length - maxLines} lines compressed] ...\n\n` + tail.join('\n');
        }
        return deduped.join('\n');
    }

    async complete(prompt, modelOverride = null) {
        const primaryModel = modelOverride || this.classifyPrompt(prompt);
        const fallbackModels = [primaryModel, "morph-v3-large", "morph-dsv4flash", "morph-v3-fast"];
        
        let lastError = null;
        for (const model of fallbackModels) {
            const url = `${this.baseUrl}/chat/completions`;
            const body = JSON.stringify({
                model: model,
                messages: [{ role: "user", content: prompt }]
            });

            try {
                const response = await fetch(url, {
                    method: 'POST',
                    headers: {
                        'Authorization': `Bearer ${this.apiKey}`,
                        'Content-Type': 'application/json'
                    },
                    body: body
                });

                const data = await response.json();
                if (data.error && data.error.type === "rate_limit_error") {
                    console.warn(`[Morph Router] Model ${model} overloaded, retrying with fallback...`);
                    lastError = data;
                    continue;
                }
                data._selected_model = model;
                return data;
            } catch (err) {
                lastError = err;
            }
        }
        return { error: lastError || "All Morph models overloaded" };
    }
}

async function cli() {
    const toolkit = new MorphToolkit();
    const args = process.argv.slice(2);
    const command = args[0];

    if (!command || command === 'help') {
        console.log(`
Universal MorphLLM Workspace Toolkit
====================================
Usage:
  node scripts/morph_router.js route "<prompt>"
  node scripts/morph_router.js compact "<text_or_filepath>"
  node scripts/morph_router.js ask "<prompt>" [model]
        `);
        return;
    }

    if (command === 'route') {
        const prompt = args.slice(1).join(' ');
        const model = toolkit.classifyPrompt(prompt);
        console.log(`Input: "${prompt}"`);
        console.log(`Routed Model: ${model}`);
    } else if (command === 'compact') {
        const target = args.slice(1).join(' ');
        const result = toolkit.compactText(target, 50);
        console.log(`Compacted Output:\n-----------------\n${result}`);
    } else if (command === 'ask') {
        const prompt = args.slice(1).join(' ');
        console.log(`[Morph Querying...]`);
        const res = await toolkit.complete(prompt);
        console.log(`[Model]: ${res._selected_model}`);
        console.log(`[Response]: ${res.choices?.[0]?.message?.content || JSON.stringify(res)}`);
    }
}

if (require.main === module) {
    cli();
}

module.exports = MorphToolkit;
