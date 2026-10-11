package org.tinygears.tinyunits.quantities;

import org.tinygears.tinyunits.Unit;

public final class Units {

    public static final Unit<Length> METRE = LengthFactory.SYSTEM_UNIT;
    public static final Unit<Length> CENTIMETRE = LengthFactory.unit("cm", 0.01);
    public static final Unit<Length> KILOMETRE = LengthFactory.unit("km", 1000);
    public static final Unit<Length> MILE = LengthFactory.unit("mi", 1609.344);

    public static final Unit<Mass> KILOGRAM = MassFactory.SYSTEM_UNIT;
    public static final Unit<Mass> GRAM = MassFactory.unit("g", 0.001);

    public static final Unit<Time> SECOND = TimeFactory.SYSTEM_UNIT;
    public static final Unit<Time> MINUTE = TimeFactory.unit("min", 60);
    public static final Unit<Time> HOUR = TimeFactory.unit("h", 3600);

    public static final Unit<Speed> METRE_PER_SECOND = SpeedFactory.SYSTEM_UNIT;
    public static final Unit<Speed> KILOMETRE_PER_HOUR = SpeedFactory.from(KILOMETRE.divide(HOUR));

    public static final Unit<Temperature> KELVIN = TemperatureFactory.SYSTEM_UNIT;
    public static final Unit<Temperature> CELSIUS = TemperatureFactory.unit("°C", 1, 273.15);

    private Units() {}
}
