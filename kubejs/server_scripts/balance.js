ServerEvents.recipes(event => {
    event.remove({ mod: 'undead_revamp2' })
    event.remove({ mod: 'sons_of_sins' })
    event.remove({ mod: 'midnight_madness' })

    // === CYCLIC — убрать навсегда (слишком OP) ===
    event.remove({ mod: 'cyclic', output: /crystal_sword/ })
    event.remove({ mod: 'cyclic', output: /crystal_pickaxe/ })
    event.remove({ mod: 'cyclic', output: /crystal_axe/ })
    event.remove({ mod: 'cyclic', output: /crystal_shovel/ })
    event.remove({ mod: 'cyclic', output: /crystal_hoe/ })
    event.remove({ mod: 'cyclic', output: /crystal_helmet/ })
    event.remove({ mod: 'cyclic', output: /crystal_chestplate/ })
    event.remove({ mod: 'cyclic', output: /crystal_leggings/ })
    event.remove({ mod: 'cyclic', output: /crystal_boots/ })
    event.remove({ mod: 'cyclic', output: /emerald_sword/ })
    event.remove({ mod: 'cyclic', output: /emerald_pickaxe/ })
    event.remove({ mod: 'cyclic', output: /emerald_axe/ })
    event.remove({ mod: 'cyclic', output: /emerald_shovel/ })
    event.remove({ mod: 'cyclic', output: /emerald_hoe/ })
    event.remove({ mod: 'cyclic', output: /emerald_helmet/ })
    event.remove({ mod: 'cyclic', output: /emerald_chestplate/ })
    event.remove({ mod: 'cyclic', output: /emerald_leggings/ })
    event.remove({ mod: 'cyclic', output: /emerald_boots/ })
    event.remove({ output: 'cyclic:ender_wings' })
    event.remove({ output: 'cyclic:bag_of_holding' })

    // === GATEWAYS — крафт жемчуга убран (только авто-спавн после дракона) ===
    event.remove({ output: 'gateways:gate_pearl' })

    // Legendary Monsters: tameable summoners break early progression.
    event.remove({ output: 'legendary_monsters:guard_summoner' })
    event.remove({ output: 'legendary_monsters:knight_summoner' })

    // Alex's Mobs: Dimensional Carver is now a smithing upgrade reward.
    event.remove({ id: 'alexsmobs:dimensional_carver' })
    event.remove({ id: 'alexsmobs:shattered_dimensional_carver' })

    // Машины Cyclic и Solar Flux панели НЕ удаляются здесь —
    // они скрыты в JEI и открываются через Схемы (client_scripts/jei_hiding.js)
})
