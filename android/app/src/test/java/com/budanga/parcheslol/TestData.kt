package com.budanga.parcheslol

import com.budanga.parcheslol.data.Change
import com.budanga.parcheslol.data.Entity
import com.budanga.parcheslol.data.GameData

fun change(
    before: String? = null,
    after: String? = null,
    verification: String = "unchecked",
    gameData: GameData? = null,
    kind: String = "stat",
    section: String = "champions",
    entity: Entity = Entity("champion", "Ezreal", "Ezreal"),
) = Change(
    id = "x", sectionTitle = "Campeones", sectionId = "patch-champions", entity = entity,
    kind = kind, before = before, after = after, text = "t", section = section,
    verification = verification, gameData = gameData,
)
