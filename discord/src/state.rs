use std::collections::HashMap;
use std::time::Instant;

use crate::gateway::GatewayEvent;
use crate::telemetry::TelemetryTracker;
use crate::types::{AccountSession, Channel, Guild, Message, Snowflake, User, UserStatus};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FocusedPanel {
    GuildsRail,
    ChannelsSidebar,
    ChatFeed,
    InputBox,
    MembersSidebar,
    AccountSwitcher,
}

pub struct AppState {
    pub accounts: Vec<AccountSession>,
    pub active_account_idx: usize,
    pub guilds: Vec<Guild>,
    pub channels: HashMap<Snowflake, Vec<Channel>>,
    pub messages: HashMap<Snowflake, Vec<Message>>,
    pub active_guild_id: Option<Snowflake>,
    pub active_channel_id: Option<Snowflake>,
    pub input_buffer: String,
    pub focused_panel: FocusedPanel,
    pub scroll_offset: usize,
    pub telemetry: TelemetryTracker,
    pub show_telemetry_hud: bool,
    pub show_account_switcher: bool,
    pub is_typing: bool,
    pub last_interaction: Instant,
    pub status_message: Option<String>,
}

impl AppState {
    pub fn new() -> Self {
        // Multi-Account Support demonstrated by Stanislav Vishnevskiy
        let primary_account = AccountSession {
            token: "phoenix_token_account_1".to_string(),
            user: User {
                id: "100000000000000001".to_string(),
                username: "stanislav_phoenix".to_string(),
                global_name: Some("Stanislav Vishnevskiy".to_string()),
                discriminator: "0".to_string(),
                avatar: None,
                bot: false,
                status: UserStatus::Online,
                custom_status: Some("Rebuilding Discord in Rust + GPUI".to_string()),
                activity: Some("Visual Studio Code".to_string()),
            },
            active_guild_id: None,
            active_channel_id: None,
            is_connected: true,
            gateway_latency_ms: 24,
        };

        let secondary_account = AccountSession {
            token: "phoenix_token_account_2".to_string(),
            user: User {
                id: "100000000000000099".to_string(),
                username: "stanislav_alt".to_string(),
                global_name: Some("Stanislav (Alt Account)".to_string()),
                discriminator: "1337".to_string(),
                avatar: None,
                bot: false,
                status: UserStatus::Idle,
                custom_status: Some("Testing 2nd session in same memory".to_string()),
                activity: Some("Discord Phoenix".to_string()),
            },
            active_guild_id: None,
            active_channel_id: None,
            is_connected: true,
            gateway_latency_ms: 29,
        };

        Self {
            accounts: vec![primary_account, secondary_account],
            active_account_idx: 0,
            guilds: Vec::new(),
            channels: HashMap::new(),
            messages: HashMap::new(),
            active_guild_id: None,
            active_channel_id: None,
            input_buffer: String::new(),
            focused_panel: FocusedPanel::InputBox,
            scroll_offset: 0,
            telemetry: TelemetryTracker::new(),
            show_telemetry_hud: true,
            show_account_switcher: false,
            is_typing: false,
            last_interaction: Instant::now(),
            status_message: Some("Discord Phoenix Gateway v10 Initialized [GPUI Direct Render]".to_string()),
        }
    }

    pub fn current_user(&self) -> &User {
        &self.accounts[self.active_account_idx].user
    }

    pub fn switch_account(&mut self, idx: usize) {
        if idx < self.accounts.len() {
            self.active_account_idx = idx;
            self.status_message = Some(format!(
                "Switched active account context to '{}' (Shared Rust memory)",
                self.accounts[idx].user.username
            ));
        }
    }

    pub fn apply_gateway_event(&mut self, event: GatewayEvent) {
        match event {
            GatewayEvent::Ready { user, guilds } => {
                if let Some(acc) = self.accounts.get_mut(self.active_account_idx) {
                    acc.user = user;
                    acc.is_connected = true;
                }
                for guild in &guilds {
                    self.channels.insert(guild.id.clone(), guild.channels.clone());
                }
                if self.active_guild_id.is_none() && !guilds.is_empty() {
                    self.active_guild_id = Some(guilds[0].id.clone());
                    if let Some(first_channel) = guilds[0].channels.first() {
                        self.active_channel_id = Some(first_channel.id.clone());
                    }
                }
                self.guilds = guilds;
                self.status_message = Some("Gateway v10 READY event processed".to_string());
            }
            GatewayEvent::MessageCreate(msg) => {
                let channel_id = msg.channel_id.clone();
                let msgs = self.messages.entry(channel_id).or_default();
                msgs.push(msg);
            }
            GatewayEvent::PresenceUpdate { user_id, status, activity } => {
                for guild in &mut self.guilds {
                    for member in &mut guild.members {
                        if member.user.id == user_id {
                            member.user.status = status.clone();
                            member.user.activity = activity.clone();
                        }
                    }
                }
            }
            GatewayEvent::TypingStart { .. } => {
                self.is_typing = true;
            }
            GatewayEvent::HeartbeatAck { latency_ms } => {
                self.telemetry.gateway_ping_ms = latency_ms;
                if let Some(acc) = self.accounts.get_mut(self.active_account_idx) {
                    acc.gateway_latency_ms = latency_ms;
                }
            }
            GatewayEvent::Disconnected(reason) => {
                self.status_message = Some(format!("Gateway: {}", reason));
            }
        }
    }

    pub fn send_current_message(&mut self) {
        let content = self.input_buffer.trim().to_string();
        if content.is_empty() {
            return;
        }

        if let Some(channel_id) = &self.active_channel_id {
            let new_msg = Message {
                id: format!("999{}", chrono::Utc::now().timestamp_millis()),
                channel_id: channel_id.clone(),
                author: self.current_user().clone(),
                content,
                timestamp: chrono::Local::now().format("%I:%M %p").to_string(),
                attachments: vec![],
                reactions: vec![],
                pinned: false,
                is_sending: false,
            };

            let msgs = self.messages.entry(channel_id.clone()).or_default();
            msgs.push(new_msg);
            self.input_buffer.clear();
            self.scroll_offset = 0;
            self.status_message = Some("Message sent via Gateway v10".to_string());
        }
    }

    pub fn active_guild(&self) -> Option<&Guild> {
        self.active_guild_id.as_ref().and_then(|id| {
            self.guilds.iter().find(|g| &g.id == id)
        })
    }

    pub fn active_channel(&self) -> Option<&Channel> {
        let guild_id = self.active_guild_id.as_ref()?;
        let channel_id = self.active_channel_id.as_ref()?;
        self.channels.get(guild_id)?.iter().find(|c| &c.id == channel_id)
    }

    pub fn active_messages(&self) -> &[Message] {
        if let Some(cid) = &self.active_channel_id {
            if let Some(msgs) = self.messages.get(cid) {
                return msgs.as_slice();
            }
        }
        &[]
    }
}
