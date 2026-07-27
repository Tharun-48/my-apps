#!/usr/bin/env python3
"""
MorphLLM Integration Router & Utility Module
Features:
 - Model Router: Classifies prompt difficulty and routes to morph/morph-v3-fast vs morph-glm52-744b.
 - Compact: Context and log compressor for removing duplicate trace lines.
 - Morph API Client: Interacts with api.morphllm.com/v1.
"""

import os
import sys
import json
import urllib.request
import urllib.error

MORPH_API_KEY = os.environ.get("MORPH_API_KEY", "sk-iEhZOp87U7Bcr-k3QhfMR2mBewnB2M39BD-RGN3tKOVoIZDT")
MORPH_BASE_URL = os.environ.get("MORPH_BASE_URL", "https://api.morphllm.com/v1")

class MorphRouter:
    def __init__(self, api_key: str = MORPH_API_KEY, base_url: str = MORPH_BASE_URL):
        self.api_key = api_key
        self.base_url = base_url.rstrip("/")

    def classify_prompt(self, prompt: str) -> str:
        """
        Classifies prompt complexity in ~50ms heuristics:
        Returns 'morph/morph-v3-fast' for quick tasks or 'morph-glm52-744b' for complex tasks.
        """
        length = len(prompt)
        complex_keywords = ["refactor", "architecture", "debug crash", "optimize", "redesign", "security"]
        
        is_complex = length > 300 or any(kw in prompt.lower() for kw in complex_keywords)
        selected_model = "morph-glm52-744b" if is_complex else "morph/morph-v3-fast"
        return selected_model

    def compact_log(self, log_content: str, max_lines: int = 100) -> str:
        """
        Context & Log Compactor: Trims duplicate stack traces and keeps essential logs.
        """
        lines = log_content.splitlines()
        if len(lines) <= max_lines:
            return log_content
        
        seen = set()
        deduped = []
        for line in lines:
            trimmed = line.strip()
            if trimmed and trimmed in seen and ("at com.example" not in trimmed):
                continue
            if trimmed:
                seen.add(trimmed)
            deduped.append(line)
        
        if len(deduped) > max_lines:
            head = deduped[:max_lines // 2]
            tail = deduped[-(max_lines // 2):]
            return "\n".join(head) + f"\n\n... [Morph Compact: {len(deduped) - max_lines} lines compressed] ...\n\n" + "\n".join(tail)
        return "\n".join(deduped)

    def complete(self, prompt: str, model: str = None) -> dict:
        if not model:
            model = self.classify_prompt(prompt)
            
        url = f"{self.base_url}/chat/completions"
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json"
        }
        payload = {
            "model": model,
            "messages": [{"role": "user", "content": prompt}]
        }
        
        req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), headers=headers, method="POST")
        try:
            with urllib.request.urlopen(req) as resp:
                data = json.loads(resp.read().decode("utf-8"))
                data["_selected_model"] = model
                return data
        except urllib.error.HTTPError as e:
            return {"error": str(e), "code": e.code, "_selected_model": model}

def main():
    router = MorphRouter()
    print("=== MorphLLM Router & Compact Utility Test ===")
    
    # 1. Test Router
    test_prompts = [
        "Fix typo in button title",
        "Refactor system monitoring architecture and optimize battery estimation algorithm in Android Kotlin"
    ]
    for p in test_prompts:
        chosen = router.classify_prompt(p)
        print(f"[Router] Prompt: '{p[:40]}...' -> Model: {chosen}")
        
    # 2. Test Compact
    dummy_log = "\n".join([f"Line {i}: Stacktrace repeating error log" for i in range(200)])
    compacted = router.compact_log(dummy_log, max_lines=20)
    print(f"\n[Compact] Original lines: 200 -> Compacted result preview:\n{compacted[:150]}...")
    
    # 3. Test API Call
    if "--test-api" in sys.argv:
        print("\n[API] Sending test completion...")
        res = router.complete("Hello Morph! Confirm router status.")
        print(f"[API Response]: Model={res.get('_selected_model')}, Output={res.get('choices', [{}])[0].get('message', {}).get('content')}")

if __name__ == "__main__":
    main()
