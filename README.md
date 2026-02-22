[//]: # (# Spring-Boot-Rest-MVC)

[//]: # ()
[//]: # ()
[//]: # ()
[//]: # ()
[//]: # ()
[//]: # ()
[//]: # (// 1. Zákazník zadá kód pri checkout)

[//]: # ()
[//]: # (Promotion promo = promotionRepository.findByCode&#40;code.toUpperCase&#40;&#41;&#41;)

[//]: # ()
[//]: # (.orElseThrow&#40;&#40;&#41; -> new PromoNotFoundException&#40;code&#41;&#41;;)

[//]: # ()
[//]: # ()
[//]: # (// 2. Validácia)

[//]: # ()
[//]: # (if &#40;!promo.isValidAt&#40;LocalDateTime.now&#40;&#41;&#41;&#41; {)

[//]: # ()
[//]: # (throw new PromoNotValidException&#40;code&#41;;)

[//]: # ()
[//]: # (})

[//]: # ()
[//]: # ()
[//]: # (// 3. Aplikuj na objednávku)

[//]: # ()
[//]: # (order.applyPromotion&#40;promo&#41;;)

[//]: # ()
[//]: # (order.recalculateTotals&#40;&#41;;)

[//]: # ()
[//]: # ()
[//]: # (// 4. Ulož oboje &#40;v jednej transakcii&#41;)

[//]: # ()
[//]: # (promo.recordUse&#40;&#41;;)

[//]: # ()
[//]: # (promotionRepository.save&#40;promo&#41;;)

[//]: # ()
[//]: # (orderRepository.save&#40;order&#41;;)
