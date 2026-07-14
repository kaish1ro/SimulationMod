// Убираем рецепты Scaling Health, сердца и запрещённые рецепты Cyclic
ServerEvents.recipes(event => {
  event.remove({ mod: 'undead_revamp2' })
  event.remove({ mod: 'sons_of_sins' })
  event.remove({ mod: 'midnight_madness' })

  // Scaling Health — все рецепты
  event.remove({ mod: 'scalinghealth' })

  // Cyclic — сердце (постоянное увеличение HP)
  event.remove({ id: 'cyclic:heart' })

  // Cyclic — руководство (patchouli книга)
  event.remove({ id: 'cyclic:guide_book' })

  // ── Cyclic: запрещённые рецепты ─────────────────────────────────────────

  // Магическая сеть
  event.remove({ output: 'cyclic:magic_net' })

  // Все улучшенные яблоки
  ;[
    'cyclic:apple_bone', 'cyclic:apple_chocolate', 'cyclic:apple_chorus',
    'cyclic:apple_ender', 'cyclic:apple_honey', 'cyclic:apple_iron',
    'cyclic:apple_lapis', 'cyclic:apple_lofty_stature', 'cyclic:apple_prismarine',
    'cyclic:apple_sprout', 'cyclic:apple_sprout_diamond', 'cyclic:apple_sprout_emerald',
    'cyclic:solidifier_apple', 'cyclic:solidifier_apple0',
    'cyclic:solidifier_apple_enchanted',
  ].forEach(id => event.remove({ output: id }))

  // Все посохи / жезлы / скипетры
  ;[
    'cyclic:antimatter_wand', 'cyclic:elevation_wand', 'cyclic:rotation_wand',
    'cyclic:teleport_wand', 'cyclic:wand_hypno', 'cyclic:wand_missile',
    'cyclic:fire_scepter', 'cyclic:ice_scepter', 'cyclic:lightning_scepter',
    'cyclic:offset_scepter', 'cyclic:replace_scepter', 'cyclic:randomize_scepter',
    'cyclic:build_scepter', 'cyclic:laser', 'cyclic:laser_cannon',
    'cyclic:flute_summoning',
  ].forEach(id => event.remove({ output: id }))

  // Все артефакты-чармы
  ;[
    'cyclic:charm_antidote', 'cyclic:charm_antipotion', 'cyclic:charm_attack_speed',
    'cyclic:charm_boostpotion', 'cyclic:charm_creeper', 'cyclic:charm_crit',
    'cyclic:charm_fire', 'cyclic:charm_home', 'cyclic:charm_invisible',
    'cyclic:charm_knockback_resistance', 'cyclic:charm_longfall', 'cyclic:charm_luck',
    'cyclic:charm_magicdefense', 'cyclic:charm_speed', 'cyclic:charm_starvation',
    'cyclic:charm_stealthpotion', 'cyclic:charm_stone', 'cyclic:charm_torch',
    'cyclic:charm_torch_cave', 'cyclic:charm_ultimate', 'cyclic:charm_venom',
    'cyclic:charm_void', 'cyclic:charm_water', 'cyclic:charm_wither',
    'cyclic:charm_world', 'cyclic:charm_xp_blocker', 'cyclic:charm_xp_speed',
  ].forEach(id => event.remove({ output: id }))

  // Эндер-крылья
  event.remove({ output: 'cyclic:charm_wing' })
  event.remove({ output: 'cyclic:chorus_flight' })
  event.remove({ output: 'cyclic:chorus_spectral' })

  // Палки с верстаком (обе)
  event.remove({ output: 'cyclic:crafting_stick' })
  event.remove({ output: 'cyclic:workbench' })

  // Расширитель инвентаря
  event.remove({ output: 'cyclic:inventory_cake' })

  // Эндер-книга
  event.remove({ output: 'cyclic:ender_book' })

  // Огнетушитель
  event.remove({ output: 'cyclic:fire_killer' })

  // ── Aether: перчатки убраны из крафта (выдаются по квестам) ─────────────────
  ;[
    'aether:leather_gloves', 'aether:iron_gloves', 'aether:golden_gloves',
    'aether:chainmail_gloves', 'aether:diamond_gloves', 'aether:zanite_gloves',
    'aether:gravitite_gloves', 'aether:netherite_gloves',
  ].forEach(id => event.remove({ output: id }))

  // Все наковальни Cyclic
  event.remove({ output: 'cyclic:anvil' })
  event.remove({ output: 'cyclic:anvil_magma' })
  event.remove({ output: 'cyclic:anvil_void' })

  // ── Cataclysm: сильное оружие и броня (WIP — нельзя получить через крафт) ──

  // Броня (превышает незерит или имеет сильные эффекты)
  ;[
    'cataclysm:cursium_helmet', 'cataclysm:cursium_chestplate',
    'cataclysm:cursium_leggings', 'cataclysm:cursium_boots',
    'cataclysm:ignitium_helmet', 'cataclysm:ignitium_chestplate',
    'cataclysm:ignitium_leggings', 'cataclysm:ignitium_boots',
    'cataclysm:ignitium_elytra_chestplate',
    'cataclysm:bone_reptile_helmet', 'cataclysm:bone_reptile_chestplate',
    'cataclysm:monstrous_helm', 'cataclysm:bloom_stone_pauldrons',
  ].forEach(id => event.remove({ output: id }))

  // Оружие и инструменты (урон > 8 или особые способности)
  ;[
    'cataclysm:ancient_spear', 'cataclysm:astrape', 'cataclysm:brontes',
    'cataclysm:ceraunus', 'cataclysm:soul_render', 'cataclysm:meat_shredder',
    'cataclysm:laser_gatling', 'cataclysm:the_annihilator',
    'cataclysm:the_immolator', 'cataclysm:the_incinerator',
    'cataclysm:wrath_of_the_desert', 'cataclysm:cursed_bow',
    'cataclysm:azure_sea_shield', 'cataclysm:black_steel_targe',
    'cataclysm:gauntlet_of_bulwark', 'cataclysm:gauntlet_of_maelstrom',
    'cataclysm:abyssal_sacrifice',
  ].forEach(id => event.remove({ output: id }))

  // Black Steel Sword: убираем оригинальный рецепт (ниже добавлен кастомный)
  event.remove({ output: 'cataclysm:black_steel_sword' })

  // ── The Undead Revamped: оружие и броня (WIP — откроются позже по прогрессии) ──
  ;[
    'undead_revamp2:bostroxset_boots', 'undead_revamp2:bostroxset_chestplate',
    'undead_revamp2:bostroxset_helmet', 'undead_revamp2:bostroxsword',
    'undead_revamp2:chainsword', 'undead_revamp2:aciddicsackbowl',
    'undead_revamp2:cloggerarmor_boots', 'undead_revamp2:cloggerarmor_chestplate',
    'undead_revamp2:cloggerarmor_helmet', 'undead_revamp2:cloggerarmor_leggings',
    'undead_revamp2:primodialarmour_boots', 'undead_revamp2:primodialarmour_chestplate',
    'undead_revamp2:primodialarmour_helmet',
  ].forEach(id => event.remove({ output: id }))
})
