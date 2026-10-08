-- my-licenses (and similar lookups) filter `metadata->>'device_id'`. With no
-- index that is a sequential scan which detoasts every licence's metadata --
-- ~178MB of base64 avatar_url across 194 rows -- and took ~18s per call,
-- tripping the statement timeout ("canceling statement due to statement
-- timeout" under the robot list on the handset).
create index if not exists licenses_metadata_device_id_idx
  on public.licenses ((metadata->>'device_id'));
