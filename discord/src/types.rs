use serde::{Deserialize, Serialize};

pub type Snowflake = String;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub enum UserStatus {
    Online,
    Idle,
    DoNotDisturb,
    Offline,
}

impl UserStatus {
    pub fn badge_symbol(&self) -> &'static str {
        match self {
            UserStatus::Online => "●",
            UserStatus::Idle => "☾",
            UserStatus::DoNotDisturb => "⛔",
            UserStatus::Offline => "○",
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct User {
    pub id: Snowflake,
    pub username: String,
    pub global_name: Option<String>,
    pub discriminator: String,
    pub avatar: Option<String>,
    pub bot: bool,
    pub status: UserStatus,
    pub custom_status: Option<String>,
    pub activity: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub enum ChannelType {
    GuildText = 0,
    DirectMessage = 1,
    GuildVoice = 2,
    GroupDm = 3,
    GuildAnnouncement = 5,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Channel {
    pub id: Snowflake,
    pub guild_id: Option<Snowflake>,
    pub name: String,
    pub kind: ChannelType,
    pub topic: Option<String>,
    pub unread: bool,
    pub mention_count: u32,
    pub last_message_id: Option<Snowflake>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Reaction {
    pub emoji: String,
    pub count: u32,
    pub me: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Attachment {
    pub id: Snowflake,
    pub filename: String,
    pub size_bytes: u64,
    pub url: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Message {
    pub id: Snowflake,
    pub channel_id: Snowflake,
    pub author: User,
    pub content: String,
    pub timestamp: String,
    pub attachments: Vec<Attachment>,
    pub reactions: Vec<Reaction>,
    pub pinned: bool,
    pub is_sending: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Role {
    pub id: Snowflake,
    pub name: String,
    pub color_rgb: (u8, u8, u8),
    pub position: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GuildMember {
    pub user: User,
    pub nick: Option<String>,
    pub roles: Vec<Snowflake>,
    pub joined_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Guild {
    pub id: Snowflake,
    pub name: String,
    pub icon_text: String,
    pub owner_id: Snowflake,
    pub channels: Vec<Channel>,
    pub members: Vec<GuildMember>,
    pub unread_count: u32,
}

#[derive(Debug, Clone)]
pub struct AccountSession {
    pub token: String,
    pub user: User,
    pub active_guild_id: Option<Snowflake>,
    pub active_channel_id: Option<Snowflake>,
    pub is_connected: bool,
    pub gateway_latency_ms: u64,
}
