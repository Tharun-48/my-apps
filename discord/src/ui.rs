use ratatui::{
    layout::{Constraint, Direction, Layout, Rect},
    style::{Color, Modifier, Style},
    text::{Line, Span},
    widgets::{Block, Borders, Clear, List, ListItem, Paragraph},
    Frame,
};

use crate::markdown::{DiscordMarkdownParser, MarkdownSpan};
use crate::state::{AppState, FocusedPanel};
use crate::types::{ChannelType, UserStatus};

// Discord Design Tokens
pub const COLOR_BLURPLE: Color = Color::Rgb(88, 101, 242);     // #5865F2
pub const COLOR_DARK_BG: Color = Color::Rgb(30, 31, 34);       // #1E1F22 (Guilds rail)
pub const COLOR_SIDEBAR: Color = Color::Rgb(43, 45, 49);       // #2B2D31 (Channels & Members)
pub const COLOR_CHAT_BG: Color = Color::Rgb(49, 51, 56);       // #313338 (Chat view)
pub const COLOR_INPUT_BG: Color = Color::Rgb(56, 58, 64);      // #383A40 (Input box)
pub const COLOR_GREEN: Color = Color::Rgb(35, 165, 90);        // #23A55A (Online / Success)
pub const COLOR_YELLOW: Color = Color::Rgb(240, 178, 50);      // #F0B232 (Idle / Warning)
pub const COLOR_RED: Color = Color::Rgb(242, 63, 67);          // #F23F43 (DND / Ping)
pub const COLOR_TEXT_NORMAL: Color = Color::Rgb(219, 222, 225);// #DBDEE1
pub const COLOR_TEXT_MUTED: Color = Color::Rgb(148, 155, 164); // #949BA4
pub const COLOR_CODE_BG: Color = Color::Rgb(43, 45, 49);

pub fn render_discord_ui(frame: &mut Frame, state: &AppState) {
    let size = frame.area();

    // Top Header / Telemetry Bar
    let main_layout = Layout::default()
        .direction(Direction::Vertical)
        .constraints([
            Constraint::Length(1), // Top Phoenix bar
            Constraint::Min(10),   // Main 3-column Discord view
            Constraint::Length(1), // Bottom Status / Shortcut Bar
        ])
        .split(size);

    render_phoenix_header(frame, main_layout[0], state);
    render_discord_columns(frame, main_layout[1], state);
    render_bottom_status_bar(frame, main_layout[2], state);

    if state.show_account_switcher {
        render_account_switcher_modal(frame, size, state);
    }
}

fn render_phoenix_header(frame: &mut Frame, area: Rect, state: &AppState) {
    let header_line = Line::from(vec![
        Span::styled(" DISCORD PHOENIX ", Style::default().bg(COLOR_BLURPLE).fg(Color::White).add_modifier(Modifier::BOLD)),
        Span::raw(" "),
        Span::styled("[Rust Unified Core + Patched GPUI]", Style::default().fg(COLOR_TEXT_MUTED)),
        Span::raw(" | "),
        Span::styled(format!("RAM: {:.1} MB", state.telemetry.memory_rss_mb), Style::default().fg(COLOR_GREEN).add_modifier(Modifier::BOLD)),
        Span::raw(" (Phoenix Target: <290MB) | "),
        Span::styled(format!("Frame: {:.2}ms ({:.0} FPS)", state.telemetry.last_frame_time.as_secs_f64() * 1000.0, state.telemetry.fps), Style::default().fg(Color::Cyan)),
        Span::raw(" | "),
        Span::styled(format!("Ping: {}ms", state.telemetry.gateway_ping_ms), Style::default().fg(COLOR_YELLOW)),
        Span::raw(" | "),
        Span::styled("CPU: Pentium N3710 [Safe 2 Threads]", Style::default().fg(COLOR_TEXT_MUTED)),
    ]);

    frame.render_widget(Paragraph::new(header_line).style(Style::default().bg(COLOR_DARK_BG)), area);
}

fn render_discord_columns(frame: &mut Frame, area: Rect, state: &AppState) {
    let columns = Layout::default()
        .direction(Direction::Horizontal)
        .constraints([
            Constraint::Length(8),  // Left Guilds rail
            Constraint::Length(26), // Channels sidebar
            Constraint::Min(40),    // Chat view (expanded)
            Constraint::Length(22), // Right Members list
        ])
        .split(area);

    render_guilds_rail(frame, columns[0], state);
    render_channels_sidebar(frame, columns[1], state);
    render_chat_view(frame, columns[2], state);
    render_members_sidebar(frame, columns[3], state);
}

fn render_guilds_rail(frame: &mut Frame, area: Rect, state: &AppState) {
    let is_focused = state.focused_panel == FocusedPanel::GuildsRail;
    let block = Block::default()
        .borders(Borders::RIGHT)
        .border_style(if is_focused { Style::default().fg(COLOR_BLURPLE) } else { Style::default().fg(Color::DarkGray) })
        .style(Style::default().bg(COLOR_DARK_BG));

    let inner = block.inner(area);
    frame.render_widget(block, area);

    let mut items = Vec::new();

    // Home / Direct Messages icon
    items.push(ListItem::new(Line::from(vec![
        Span::styled("  ", Style::default()),
        Span::styled(" 💬 ", Style::default().bg(COLOR_BLURPLE).fg(Color::White).add_modifier(Modifier::BOLD)),
    ])));
    items.push(ListItem::new(Line::from(Span::styled(" ──────", Style::default().fg(Color::DarkGray)))));

    // Guild icons with unread badges
    for (i, guild) in state.guilds.iter().enumerate() {
        let is_active = state.active_guild_id.as_ref() == Some(&guild.id);
        let pill = if is_active { "▍" } else if guild.unread_count > 0 { "•" } else { " " };
        let icon_bg = if is_active { COLOR_BLURPLE } else { COLOR_SIDEBAR };

        let mut spans = vec![
            Span::styled(format!("{} ", pill), Style::default().fg(Color::White).add_modifier(Modifier::BOLD)),
            Span::styled(format!(" {} ", guild.icon_text), Style::default().bg(icon_bg).fg(Color::White).add_modifier(Modifier::BOLD)),
        ];

        if guild.unread_count > 0 {
            spans.push(Span::styled(format!(" +{}", guild.unread_count), Style::default().fg(COLOR_RED).add_modifier(Modifier::BOLD)));
        }

        items.push(ListItem::new(Line::from(spans)));
        if i < state.guilds.len() - 1 {
            items.push(ListItem::new(Line::from(Span::raw(""))));
        }
    }

    // Add server button
    items.push(ListItem::new(Line::from(Span::raw(""))));
    items.push(ListItem::new(Line::from(vec![
        Span::styled("   ", Style::default()),
        Span::styled(" + ", Style::default().bg(COLOR_GREEN).fg(Color::White).add_modifier(Modifier::BOLD)),
    ])));

    frame.render_widget(List::new(items), inner);
}

fn render_channels_sidebar(frame: &mut Frame, area: Rect, state: &AppState) {
    let is_focused = state.focused_panel == FocusedPanel::ChannelsSidebar;
    let block = Block::default()
        .borders(Borders::RIGHT)
        .border_style(if is_focused { Style::default().fg(COLOR_BLURPLE) } else { Style::default().fg(Color::DarkGray) })
        .style(Style::default().bg(COLOR_SIDEBAR));

    let inner = block.inner(area);
    frame.render_widget(block, area);

    let layout = Layout::default()
        .direction(Direction::Vertical)
        .constraints([
            Constraint::Length(2), // Server Name Header
            Constraint::Min(6),    // Channel List
            Constraint::Length(3), // User Profile Tray at bottom
        ])
        .split(inner);

    // 1. Server Header
    let server_name = state.active_guild().map(|g| g.name.as_str()).unwrap_or("Discord Phoenix");
    let header = Paragraph::new(Line::from(vec![
        Span::styled(server_name, Style::default().fg(Color::White).add_modifier(Modifier::BOLD)),
        Span::styled(" ▾", Style::default().fg(COLOR_TEXT_MUTED)),
    ])).style(Style::default().bg(COLOR_SIDEBAR));
    frame.render_widget(header, layout[0]);

    // 2. Channel List
    let mut channel_items = Vec::new();
    if let Some(guild_id) = &state.active_guild_id {
        if let Some(channels) = state.channels.get(guild_id) {
            channel_items.push(ListItem::new(Line::from(Span::styled("▾ TEXT CHANNELS", Style::default().fg(COLOR_TEXT_MUTED).add_modifier(Modifier::BOLD)))));

            for ch in channels {
                let is_active = state.active_channel_id.as_ref() == Some(&ch.id);
                let (prefix, color) = match ch.kind {
                    ChannelType::GuildText => ("#", if is_active { Color::White } else { COLOR_TEXT_NORMAL }),
                    ChannelType::GuildVoice => ("🔊", if is_active { COLOR_GREEN } else { COLOR_TEXT_MUTED }),
                    ChannelType::GuildAnnouncement => ("📢", COLOR_YELLOW),
                    _ => ("#", COLOR_TEXT_NORMAL),
                };

                let mut spans = vec![
                    Span::raw("  "),
                    Span::styled(format!("{} ", prefix), Style::default().fg(COLOR_TEXT_MUTED)),
                    Span::styled(&ch.name, Style::default().fg(color).add_modifier(if is_active { Modifier::BOLD } else { Modifier::empty() })),
                ];

                if ch.mention_count > 0 {
                    spans.push(Span::styled(format!(" [{}]", ch.mention_count), Style::default().bg(COLOR_RED).fg(Color::White).add_modifier(Modifier::BOLD)));
                }

                let style = if is_active {
                    Style::default().bg(COLOR_CHAT_BG)
                } else {
                    Style::default()
                };

                channel_items.push(ListItem::new(Line::from(spans)).style(style));
            }
        }
    }
    frame.render_widget(List::new(channel_items), layout[1]);

    // 3. User Tray
    let user = state.current_user();
    let status_color = match user.status {
        UserStatus::Online => COLOR_GREEN,
        UserStatus::Idle => COLOR_YELLOW,
        UserStatus::DoNotDisturb => COLOR_RED,
        UserStatus::Offline => Color::Gray,
    };

    let user_tray = Paragraph::new(vec![
        Line::from(vec![
            Span::styled(user.status.badge_symbol(), Style::default().fg(status_color)),
            Span::raw(" "),
            Span::styled(&user.username, Style::default().fg(Color::White).add_modifier(Modifier::BOLD)),
            Span::styled(" [Acc #", Style::default().fg(COLOR_TEXT_MUTED)),
            Span::styled(format!("{}", state.active_account_idx + 1), Style::default().fg(COLOR_BLURPLE).add_modifier(Modifier::BOLD)),
            Span::styled("]", Style::default().fg(COLOR_TEXT_MUTED)),
        ]),
        Line::from(vec![
            Span::styled(user.custom_status.as_deref().unwrap_or("Online"), Style::default().fg(COLOR_TEXT_MUTED)),
            Span::raw(" 🎙️ 🎧 ⚙️"),
        ]),
    ]).style(Style::default().bg(COLOR_DARK_BG));
    frame.render_widget(user_tray, layout[2]);
}

fn render_chat_view(frame: &mut Frame, area: Rect, state: &AppState) {
    let layout = Layout::default()
        .direction(Direction::Vertical)
        .constraints([
            Constraint::Length(2), // Channel Header Bar
            Constraint::Min(8),    // Virtualized Message Feed
            Constraint::Length(1), // Typing indicator
            Constraint::Length(3), // Input Box
        ])
        .split(area);

    // 1. Channel Header
    let (ch_name, topic) = if let Some(ch) = state.active_channel() {
        (format!("# {}", ch.name), ch.topic.as_deref().unwrap_or("No topic set"))
    } else {
        ("# select-a-channel".to_string(), "Discord Phoenix Direct Render")
    };

    let header = Paragraph::new(Line::from(vec![
        Span::styled(ch_name, Style::default().fg(Color::White).add_modifier(Modifier::BOLD)),
        Span::raw("  |  "),
        Span::styled(topic, Style::default().fg(COLOR_TEXT_MUTED)),
        Span::raw("  🔔  📌  👥"),
    ])).style(Style::default().bg(COLOR_CHAT_BG));
    frame.render_widget(header, layout[0]);

    // 2. Virtualized Messages Feed
    let _is_focused = state.focused_panel == FocusedPanel::ChatFeed;
    let messages_block = Block::default()
        .borders(Borders::NONE)
        .style(Style::default().bg(COLOR_CHAT_BG));
    let msg_inner = messages_block.inner(layout[1]);
    frame.render_widget(messages_block, layout[1]);

    let messages = state.active_messages();
    let mut message_items = Vec::new();

    for msg in messages {
        let is_self = msg.author.id == state.current_user().id;
        let author_color = if is_self { COLOR_BLURPLE } else { Color::Cyan };

        // Header: Author Avatar/Name + Timestamp
        let mut header_spans = vec![
            Span::styled(format!(" {} ", msg.author.username), Style::default().fg(author_color).add_modifier(Modifier::BOLD)),
        ];
        if msg.author.bot {
            header_spans.push(Span::styled(" BOT ", Style::default().bg(COLOR_BLURPLE).fg(Color::White).add_modifier(Modifier::BOLD)));
            header_spans.push(Span::raw(" "));
        }
        header_spans.push(Span::styled(&msg.timestamp, Style::default().fg(COLOR_TEXT_MUTED)));

        if msg.pinned {
            header_spans.push(Span::styled(" [📌 PINNED]", Style::default().fg(COLOR_YELLOW)));
        }
        if msg.is_sending {
            header_spans.push(Span::styled(" [Sending...]", Style::default().fg(Color::DarkGray)));
        }

        message_items.push(ListItem::new(Line::from(header_spans)));

        // Parsed Discord Markdown Content
        let parsed_spans = DiscordMarkdownParser::parse(&msg.content);
        let mut line_spans = vec![Span::raw("   ")];

        for span in parsed_spans {
            match span {
                MarkdownSpan::Text(t) => line_spans.push(Span::styled(t, Style::default().fg(COLOR_TEXT_NORMAL))),
                MarkdownSpan::Bold(b) => line_spans.push(Span::styled(b, Style::default().fg(Color::White).add_modifier(Modifier::BOLD))),
                MarkdownSpan::Italic(i) => line_spans.push(Span::styled(i, Style::default().fg(COLOR_TEXT_NORMAL).add_modifier(Modifier::ITALIC))),
                MarkdownSpan::BoldItalic(bi) => line_spans.push(Span::styled(bi, Style::default().fg(Color::White).add_modifier(Modifier::BOLD | Modifier::ITALIC))),
                MarkdownSpan::Strikethrough(s) => line_spans.push(Span::styled(s, Style::default().fg(COLOR_TEXT_MUTED).add_modifier(Modifier::CROSSED_OUT))),
                MarkdownSpan::Underline(u) => line_spans.push(Span::styled(u, Style::default().fg(COLOR_TEXT_NORMAL).add_modifier(Modifier::UNDERLINED))),
                MarkdownSpan::InlineCode(c) => line_spans.push(Span::styled(format!(" {} ", c), Style::default().bg(COLOR_CODE_BG).fg(COLOR_GREEN))),
                MarkdownSpan::CodeBlock { lang, code } => {
                    if !line_spans.is_empty() {
                        message_items.push(ListItem::new(Line::from(std::mem::take(&mut line_spans))));
                    }
                    let tag = lang.as_deref().unwrap_or("code");
                    message_items.push(ListItem::new(Line::from(vec![
                        Span::raw("   "),
                        Span::styled(format!("┌── {} ────────────────────────────", tag), Style::default().fg(Color::DarkGray)),
                    ])));
                    for code_line in code.lines() {
                        message_items.push(ListItem::new(Line::from(vec![
                            Span::raw("   "),
                            Span::styled("│ ", Style::default().fg(Color::DarkGray)),
                            Span::styled(code_line.to_string(), Style::default().fg(COLOR_GREEN)),
                        ])));
                    }
                    message_items.push(ListItem::new(Line::from(vec![
                        Span::raw("   "),
                        Span::styled("└───────────────────────────────────", Style::default().fg(Color::DarkGray)),
                    ])));
                    line_spans = vec![Span::raw("   ")];
                }
                MarkdownSpan::Spoiler(sp) => line_spans.push(Span::styled(format!(" [SPOILER: {}] ", sp), Style::default().bg(Color::Black).fg(Color::DarkGray))),
                MarkdownSpan::Mention(m) => line_spans.push(Span::styled(m, Style::default().bg(COLOR_BLURPLE).fg(Color::White))),
                MarkdownSpan::ChannelLink(cl) => line_spans.push(Span::styled(cl, Style::default().fg(COLOR_BLURPLE).add_modifier(Modifier::UNDERLINED))),
                MarkdownSpan::CustomEmoji { name, .. } => line_spans.push(Span::styled(format!(":{}:", name), Style::default().fg(COLOR_YELLOW))),
                MarkdownSpan::Blockquote(b) => line_spans.push(Span::styled(format!("▎ {}", b), Style::default().fg(COLOR_TEXT_MUTED))),
            }
        }

        if line_spans.len() > 1 {
            message_items.push(ListItem::new(Line::from(line_spans)));
        }

        // Reactions Pills
        if !msg.reactions.is_empty() {
            let mut reaction_spans = vec![Span::raw("   ")];
            for r in &msg.reactions {
                let r_bg = if r.me { COLOR_BLURPLE } else { COLOR_SIDEBAR };
                reaction_spans.push(Span::styled(format!(" {} {} ", r.emoji, r.count), Style::default().bg(r_bg).fg(Color::White)));
                reaction_spans.push(Span::raw(" "));
            }
            message_items.push(ListItem::new(Line::from(reaction_spans)));
        }

        message_items.push(ListItem::new(Line::from(Span::raw(""))));
    }

    let messages_list = List::new(message_items);
    frame.render_widget(messages_list, msg_inner);

    // 3. Typing Indicator
    let typing_text = if state.is_typing {
        Line::from(vec![
            Span::styled("● ● ● ", Style::default().fg(COLOR_GREEN)),
            Span::styled("Someone is typing in this channel...", Style::default().fg(COLOR_TEXT_MUTED).add_modifier(Modifier::ITALIC)),
        ])
    } else {
        Line::from(Span::raw(""))
    };
    frame.render_widget(Paragraph::new(typing_text).style(Style::default().bg(COLOR_CHAT_BG)), layout[2]);

    // 4. Discord Message Input Box
    let is_input_focused = state.focused_panel == FocusedPanel::InputBox;
    let input_placeholder = state.active_channel().map(|c| format!("Message #{}", c.name)).unwrap_or_else(|| "Message...".to_string());

    let content_span = if state.input_buffer.is_empty() {
        Span::styled(input_placeholder, Style::default().fg(COLOR_TEXT_MUTED))
    } else {
        Span::styled(&state.input_buffer, Style::default().fg(Color::White))
    };

    let input_block = Block::default()
        .borders(Borders::ALL)
        .border_style(if is_input_focused { Style::default().fg(COLOR_BLURPLE) } else { Style::default().fg(Color::DarkGray) })
        .style(Style::default().bg(COLOR_INPUT_BG));

    let input_inner = input_block.inner(layout[3]);
    frame.render_widget(input_block, layout[3]);

    let input_line = Line::from(vec![
        Span::styled(" + ", Style::default().bg(COLOR_SIDEBAR).fg(Color::White).add_modifier(Modifier::BOLD)),
        Span::raw(" "),
        content_span,
        Span::styled(" 🎁 GIF 😊", Style::default().fg(COLOR_TEXT_MUTED)),
    ]);

    frame.render_widget(Paragraph::new(input_line).style(Style::default().bg(COLOR_INPUT_BG)), input_inner);
}

fn render_members_sidebar(frame: &mut Frame, area: Rect, state: &AppState) {
    let is_focused = state.focused_panel == FocusedPanel::MembersSidebar;
    let block = Block::default()
        .borders(Borders::LEFT)
        .border_style(if is_focused { Style::default().fg(COLOR_BLURPLE) } else { Style::default().fg(Color::DarkGray) })
        .style(Style::default().bg(COLOR_SIDEBAR));

    let inner = block.inner(area);
    frame.render_widget(block, area);

    let mut items = Vec::new();

    if let Some(guild) = state.active_guild() {
        let online_count = guild.members.iter().filter(|m| m.user.status != UserStatus::Offline).count();
        items.push(ListItem::new(Line::from(Span::styled(format!("ONLINE — {}", online_count), Style::default().fg(COLOR_TEXT_MUTED).add_modifier(Modifier::BOLD)))));

        for member in &guild.members {
            let status_color = match member.user.status {
                UserStatus::Online => COLOR_GREEN,
                UserStatus::Idle => COLOR_YELLOW,
                UserStatus::DoNotDisturb => COLOR_RED,
                UserStatus::Offline => Color::Gray,
            };

            let name = member.nick.as_deref().unwrap_or(&member.user.username);
            let mut name_spans = vec![
                Span::styled(member.user.status.badge_symbol(), Style::default().fg(status_color)),
                Span::raw(" "),
                Span::styled(name, Style::default().fg(COLOR_TEXT_NORMAL)),
            ];

            if member.user.bot {
                name_spans.push(Span::styled(" BOT", Style::default().bg(COLOR_BLURPLE).fg(Color::White)));
            }

            items.push(ListItem::new(Line::from(name_spans)));

            if let Some(act) = &member.user.activity {
                items.push(ListItem::new(Line::from(vec![
                    Span::raw("  "),
                    Span::styled(act, Style::default().fg(COLOR_TEXT_MUTED)),
                ])));
            }
        }
    }

    frame.render_widget(List::new(items), inner);
}

fn render_bottom_status_bar(frame: &mut Frame, area: Rect, state: &AppState) {
    let status_text = state.status_message.as_deref().unwrap_or("Ready");
    let bar_line = Line::from(vec![
        Span::styled(" [Tab] ", Style::default().fg(COLOR_BLURPLE).add_modifier(Modifier::BOLD)),
        Span::raw("Switch Panel | "),
        Span::styled(" [F2] ", Style::default().fg(COLOR_BLURPLE).add_modifier(Modifier::BOLD)),
        Span::raw("Multi-Account Switcher | "),
        Span::styled(" [Enter] ", Style::default().fg(COLOR_BLURPLE).add_modifier(Modifier::BOLD)),
        Span::raw("Send Message | "),
        Span::styled(" [Esc] ", Style::default().fg(COLOR_BLURPLE).add_modifier(Modifier::BOLD)),
        Span::raw("Quit | "),
        Span::styled(format!("Status: {}", status_text), Style::default().fg(COLOR_TEXT_MUTED)),
    ]);

    frame.render_widget(Paragraph::new(bar_line).style(Style::default().bg(COLOR_DARK_BG)), area);
}

fn render_account_switcher_modal(frame: &mut Frame, area: Rect, state: &AppState) {
    let popup_area = Rect {
        x: area.width / 4,
        y: area.height / 4,
        width: area.width / 2,
        height: area.height / 2,
    };

    frame.render_widget(Clear, popup_area);

    let block = Block::default()
        .borders(Borders::ALL)
        .title(" 👥 Discord Phoenix Multi-Account Switcher (Shared Rust Core) ")
        .border_style(Style::default().fg(COLOR_BLURPLE))
        .style(Style::default().bg(COLOR_SIDEBAR));

    let inner = block.inner(popup_area);
    frame.render_widget(block, popup_area);

    let mut items = Vec::new();
    items.push(ListItem::new(Line::from(Span::styled(
        "Demonstrated by Stanislav Vishnevskiy: 2+ accounts running in a single lightweight Rust memory space (<290MB RAM)!",
        Style::default().fg(COLOR_TEXT_MUTED).add_modifier(Modifier::ITALIC),
    ))));
    items.push(ListItem::new(Line::from(Span::raw(""))));

    for (i, acc) in state.accounts.iter().enumerate() {
        let is_active = i == state.active_account_idx;
        let active_badge = if is_active { " [ACTIVE]" } else { "" };
        let color = if is_active { COLOR_GREEN } else { COLOR_TEXT_NORMAL };

        items.push(ListItem::new(Line::from(vec![
            Span::styled(format!("{}. @{}#{}{}", i + 1, acc.user.username, acc.user.discriminator, active_badge), Style::default().fg(color).add_modifier(Modifier::BOLD)),
            Span::raw(" - Latency: "),
            Span::styled(format!("{}ms", acc.gateway_latency_ms), Style::default().fg(COLOR_YELLOW)),
        ])));
    }

    items.push(ListItem::new(Line::from(Span::raw(""))));
    items.push(ListItem::new(Line::from(Span::styled("Press [1], [2] to switch account, or [F2] / [Esc] to close", Style::default().fg(Color::DarkGray)))));

    frame.render_widget(List::new(items), inner);
}
