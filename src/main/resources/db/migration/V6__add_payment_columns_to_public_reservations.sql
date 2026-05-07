ALTER TABLE public_reservations
    ADD COLUMN payment_intent_id VARCHAR(255);

ALTER TABLE public_reservations
    ADD COLUMN payment_status VARCHAR(30);

UPDATE public_reservations
SET payment_status = 'UNSPECIFIED'
WHERE payment_status IS NULL;

ALTER TABLE public_reservations
    ALTER COLUMN payment_status SET NOT NULL;

CREATE UNIQUE INDEX uk_public_reservations_payment_intent
    ON public_reservations (payment_intent_id);
