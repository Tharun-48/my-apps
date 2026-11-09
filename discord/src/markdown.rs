#[derive(Debug, Clone, PartialEq, Eq)]
pub enum MarkdownSpan {
    Text(String),
    Bold(String),
    Italic(String),
    BoldItalic(String),
    Strikethrough(String),
    Underline(String),
    InlineCode(String),
    CodeBlock { lang: Option<String>, code: String },
    Spoiler(String),
    Mention(String),
    ChannelLink(String),
    CustomEmoji { name: String, id: String },
    Blockquote(String),
}

pub struct DiscordMarkdownParser;

impl DiscordMarkdownParser {
    pub fn parse(input: &str) -> Vec<MarkdownSpan> {
        let mut spans = Vec::new();
        let trimmed = input.trim_end();

        // Check for multiline codeblock first: ```rust ... ```
        if trimmed.starts_with("```") && trimmed.ends_with("```") && trimmed.len() >= 6 {
            let inner = &trimmed[3..trimmed.len() - 3];
            let (lang, code) = if let Some(newline_pos) = inner.find('\n') {
                let first_line = inner[..newline_pos].trim();
                let rest = &inner[newline_pos + 1..];
                (if first_line.is_empty() { None } else { Some(first_line.to_string()) }, rest.to_string())
            } else {
                (None, inner.to_string())
            };
            return vec![MarkdownSpan::CodeBlock { lang, code }];
        }

        // Check for blockquote
        if trimmed.starts_with("> ") {
            return vec![MarkdownSpan::Blockquote(trimmed[2..].to_string())];
        }

        // Fast token-based span scanning
        let chars: Vec<char> = input.chars().collect();
        let mut idx = 0;
        let len = chars.len();
        let mut current_text = String::new();

        while idx < len {
            // Spoiler: ||...||
            if idx + 1 < len && chars[idx] == '|' && chars[idx + 1] == '|' {
                if !current_text.is_empty() {
                    spans.push(MarkdownSpan::Text(std::mem::take(&mut current_text)));
                }
                idx += 2;
                let start = idx;
                while idx + 1 < len && !(chars[idx] == '|' && chars[idx + 1] == '|') {
                    idx += 1;
                }
                let spoiler_str: String = chars[start..idx].iter().collect();
                spans.push(MarkdownSpan::Spoiler(spoiler_str));
                if idx + 1 < len {
                    idx += 2;
                }
                continue;
            }

            // Bold Italic: ***...***
            if idx + 2 < len && chars[idx] == '*' && chars[idx + 1] == '*' && chars[idx + 2] == '*' {
                if !current_text.is_empty() {
                    spans.push(MarkdownSpan::Text(std::mem::take(&mut current_text)));
                }
                idx += 3;
                let start = idx;
                while idx + 2 < len && !(chars[idx] == '*' && chars[idx + 1] == '*' && chars[idx + 2] == '*') {
                    idx += 1;
                }
                let s: String = chars[start..idx].iter().collect();
                spans.push(MarkdownSpan::BoldItalic(s));
                if idx + 2 < len {
                    idx += 3;
                }
                continue;
            }

            // Bold: **...**
            if idx + 1 < len && chars[idx] == '*' && chars[idx + 1] == '*' {
                if !current_text.is_empty() {
                    spans.push(MarkdownSpan::Text(std::mem::take(&mut current_text)));
                }
                idx += 2;
                let start = idx;
                while idx + 1 < len && !(chars[idx] == '*' && chars[idx + 1] == '*') {
                    idx += 1;
                }
                let s: String = chars[start..idx].iter().collect();
                spans.push(MarkdownSpan::Bold(s));
                if idx + 1 < len {
                    idx += 2;
                }
                continue;
            }

            // Strikethrough: ~~...~~
            if idx + 1 < len && chars[idx] == '~' && chars[idx + 1] == '~' {
                if !current_text.is_empty() {
                    spans.push(MarkdownSpan::Text(std::mem::take(&mut current_text)));
                }
                idx += 2;
                let start = idx;
                while idx + 1 < len && !(chars[idx] == '~' && chars[idx + 1] == '~') {
                    idx += 1;
                }
                let s: String = chars[start..idx].iter().collect();
                spans.push(MarkdownSpan::Strikethrough(s));
                if idx + 1 < len {
                    idx += 2;
                }
                continue;
            }

            // Underline: __...__
            if idx + 1 < len && chars[idx] == '_' && chars[idx + 1] == '_' {
                if !current_text.is_empty() {
                    spans.push(MarkdownSpan::Text(std::mem::take(&mut current_text)));
                }
                idx += 2;
                let start = idx;
                while idx + 1 < len && !(chars[idx] == '_' && chars[idx + 1] == '_') {
                    idx += 1;
                }
                let s: String = chars[start..idx].iter().collect();
                spans.push(MarkdownSpan::Underline(s));
                if idx + 1 < len {
                    idx += 2;
                }
                continue;
            }

            // Inline code: `...`
            if chars[idx] == '`' {
                if !current_text.is_empty() {
                    spans.push(MarkdownSpan::Text(std::mem::take(&mut current_text)));
                }
                idx += 1;
                let start = idx;
                while idx < len && chars[idx] != '`' {
                    idx += 1;
                }
                let s: String = chars[start..idx].iter().collect();
                spans.push(MarkdownSpan::InlineCode(s));
                if idx < len {
                    idx += 1;
                }
                continue;
            }

            // Mention or Channel or Custom Emoji: <@...>, <#...>, <:name:id>
            if chars[idx] == '<' {
                let start = idx;
                let mut found_close = false;
                while idx < len && chars[idx] != '>' {
                    idx += 1;
                }
                if idx < len && chars[idx] == '>' {
                    found_close = true;
                    idx += 1;
                }
                if found_close {
                    let tag: String = chars[start..idx].iter().collect();
                    if tag.starts_with("<@") {
                        if !current_text.is_empty() {
                            spans.push(MarkdownSpan::Text(std::mem::take(&mut current_text)));
                        }
                        spans.push(MarkdownSpan::Mention(tag));
                        continue;
                    } else if tag.starts_with("<#") {
                        if !current_text.is_empty() {
                            spans.push(MarkdownSpan::Text(std::mem::take(&mut current_text)));
                        }
                        spans.push(MarkdownSpan::ChannelLink(tag));
                        continue;
                    } else if tag.starts_with("<:") {
                        let parts: Vec<&str> = tag.trim_matches(|c| c == '<' || c == '>').split(':').collect();
                        if parts.len() >= 3 {
                            if !current_text.is_empty() {
                                spans.push(MarkdownSpan::Text(std::mem::take(&mut current_text)));
                            }
                            spans.push(MarkdownSpan::CustomEmoji {
                                name: parts[1].to_string(),
                                id: parts[2].to_string(),
                            });
                            continue;
                        }
                    }
                    current_text.push_str(&tag);
                    continue;
                }
                idx = start;
            }

            current_text.push(chars[idx]);
            idx += 1;
        }

        if !current_text.is_empty() {
            spans.push(MarkdownSpan::Text(current_text));
        }

        spans
    }
}
