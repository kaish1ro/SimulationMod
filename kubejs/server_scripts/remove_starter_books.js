// Убираем нежелательные стартовые книги при входе игрока
// (Cyclic выдаёт patchouli:guide_book через собственный event, конфига нет)
PlayerEvents.loggedIn(event => {
  const player = event.player
  const inv = player.inventory

  for (let i = 0; i < inv.containerSize; i++) {
    const stack = inv.getItem(i)
    if (stack.isEmpty()) continue
    if (stack.id !== 'patchouli:guide_book') continue

    // Проверяем NBT — убираем только Cyclic-книгу
    const nbt = stack.nbt
    if (nbt && nbt['patchouli:book'] === 'cyclic:guide_book') {
      inv.setItem(i, Item.of('minecraft:air'))
    }
  }
})
