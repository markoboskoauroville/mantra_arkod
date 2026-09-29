# Momentary updates: Marko's requests, word for word

Every new request lands here first and is pushed before any code, so a quota cut loses nothing.
"Continue" means: read this file first.

## 29.9.2026, the owner sheet (vlasnički list)

> now we are working for mantra trail We need to add additional data. You can break it in, in 3 tabs
> actually, each, each of this data. And one tab is land use, second tab possession sheet, which we
> have. What we are missing and we need to upgrade, it's an owner sheet, or how we call it in Croatia,
> vlasnički list. So beside Posjedovni list, we need to have Vlasnički list. You need to find a way how
> to enter these parcel numbers and find that data. Should be publicly available, I think, on different
> website, and one data needs to go to the other site and then get the data back

(Screenshot: parcel 3700/11, k.o. DRENOVA 324523, 3 071 m², KUČIĆKI PUT; LAND USE, then
POSSESSION SHEET 1615 with the possessors and their shares.)

1. The sheet in three tabs: LAND USE · POSSESSION SHEET (posjedovni list) · OWNER SHEET (vlasnički list).
   **Status:** done in v94 (29.9.2026), proved on the emulator.
2. The owner sheet: from the parcel's number, fetch the land registry (zemljišna knjiga) folio's
   B list (the owners) from the public source and show it. **Status:** done in v94. The source is
   OSS's public land registry (`lr/lr-unit`). Linked parcels open by themselves; where the state has no
   link (all of Drenova), he types the land-book parcel or the z.k. uložak once, and it is kept.
