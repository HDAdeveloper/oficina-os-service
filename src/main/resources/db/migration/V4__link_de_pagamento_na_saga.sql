-- Pagamento por link: a Saga guarda o link que o Billing gerou para o cliente pagar.
ALTER TABLE saga_instance ADD COLUMN link_pagamento VARCHAR(500);
