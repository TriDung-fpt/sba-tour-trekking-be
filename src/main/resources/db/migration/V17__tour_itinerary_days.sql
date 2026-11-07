
CREATE TABLE trekking.tour_itinerary_days (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tour_id BIGINT NOT NULL REFERENCES trekking.tours(id) ON DELETE CASCADE,
    day_number INTEGER NOT NULL CHECK (day_number > 0),
    title VARCHAR(250) NOT NULL CHECK (btrim(title) <> ''),
    meals TEXT,
    overnight TEXT,
    UNIQUE (tour_id, day_number)
);
