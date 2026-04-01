create table if not exists hypergraph_ltm (
    id text primary key,
    memory_id text not null,
    message_type text not null,
    message_text text,
    tool_request_id text,
    tool_name text,
    weight real not null default 1.0,
    importance real not null,
    tier text not null,
    ltm_level integer not null default 0,
    summary text,
    archive_pointers text,
    node_ids text,
    access_count integer not null default 1,
    anchor integer not null default 0,
    created_at text not null,
    last_accessed_at text not null,
    emotion_tag text,
    context_tags text,
    linked_edge_ids text,
    meta_edge_id text
);

create index if not exists idx_hypergraph_ltm_memory_id on hypergraph_ltm(memory_id);
create index if not exists idx_hypergraph_ltm_created_at on hypergraph_ltm(created_at);

create table if not exists hypergraph_nodes (
    id text primary key,
    memory_id text not null,
    content text not null,
    node_type text not null,
    embedding_json text,
    created_at text not null,
    last_accessed_at text not null,
    access_count integer not null default 1
);

create index if not exists idx_hypergraph_nodes_memory_id on hypergraph_nodes(memory_id);

create table if not exists hypergraph_meta_edges (
    id text primary key,
    member_edge_ids text not null,
    theme_summary text not null,
    linked_meta_edge_ids text,
    context_tags text,
    cohesion real not null default 0.0,
    created_at text not null,
    updated_at text not null
);

create table if not exists hypergraph_meta_edge_links (
    meta_id_a text not null,
    meta_id_b text not null,
    primary key (meta_id_a, meta_id_b)
);

create index if not exists idx_hypergraph_ltm_ltm_level on hypergraph_ltm(ltm_level);
create index if not exists idx_hypergraph_ltm_meta_edge_id on hypergraph_ltm(meta_edge_id);
