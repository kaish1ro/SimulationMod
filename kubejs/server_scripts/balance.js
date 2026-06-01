ServerEvents.recipes(event => {
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

    // Машины Cyclic и Solar Flux панели НЕ удаляются здесь —
    // они скрыты в JEI и открываются через Схемы (client_scripts/jei_hiding.js)
})
