// Удаляем все рецепты Scaling Health (контейнеры сердец и маны - они в Cyclic)
ServerEvents.recipes(event => {
  event.remove({ mod: 'scalinghealth' })
})
