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
    summary text,
    archive_pointers text,
    node_ids text,
    access_count integer not null default 1,
    anchor integer not null default 0,
    created_at text not null,
    last_accessed_at text not null,
    emotion_tag text,
    context_tags text,
    linked_edge_ids text
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
