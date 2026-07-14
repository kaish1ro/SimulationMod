// Кастомные рецепты Cataclysm
ServerEvents.recipes(event => {

  // Меч из чёрной стали: железный меч + 8 слитков чёрной стали по периметру
  // Эффект: 20% шанс восстановить 1 сердце при попадании (реализован в SimulationMod)
  event.shaped('cataclysm:black_steel_sword', [
    'BBB',
    'BIB',
    'BBB'
  ], {
    B: 'cataclysm:black_steel_ingot',
    I: 'minecraft:iron_sword'
  })

})
