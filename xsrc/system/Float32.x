const Float32
        extends BinaryFPNumber
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 32-bit binary floating point number (a "single float") from its bitwise machine
     * representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from Least
     *              Significant Bit (LSB) in the `0` element, to Most Significant Bit (MSB) in the
     *              `size-1` element
     */
    construct(Bit[] bits)
        {
        assert bits.size == 32;
        construct BinaryFPNumber(bits);
        }

    /**
     * Construct a 32-bit binary floating point number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        assert bytes.size == 4;
        construct BinaryFPNumber(bytes);
        }


    // ----- Number properties ---------------------------------------------------------------------

    @Override
    Signum sign.get()
        {
        TODO need to think this through carefully because there is a sign bit and both +/-0
        }


    // ----- Number operations ---------------------------------------------------------------------

    @Override
    @Op Float32 add(Float32 n)
        {
        TODO
        }

    @Override
    @Op Float32 sub(Float32 n)
        {
        TODO
        }

    @Override
    @Op Float32 mul(Float32 n)
        {
        TODO
        }

    @Override
    @Op Float32 div(Float32 n)
        {
        TODO
        }

    @Override
    @Op Float32 mod(Float32 n)
        {
        TODO
        }

    @Override
    Float32 abs()
        {
        return this < 0 ? -this : this;
        }

    @Override
    @Op Float32 neg()
        {
        TODO
        }

    @Override
    Float32 pow(Float32 n)
        {
        TODO
        }


    // ----- FPNumber properties -------------------------------------------------------------------

    @Override
    Boolean finite.get()
        {
        TODO
        }

    @Override
    Boolean infinite.get()
        {
        TODO
        }

    @Override
    Boolean NaN.get()
        {
        TODO
        }


    // ----- FPNumber operations -------------------------------------------------------------------

    @Override
    Float32 round()
        {
        TODO
        }

    @Override
    Float32 floor()
        {
        TODO
        }

    @Override
    Float32 ceil()
        {
        TODO
        }

    @Override
    Float32 exp()
        {
        TODO
        }

    @Override
    Float32 log()
        {
        TODO
        }

    @Override
    Float32 log10()
        {
        TODO
        }

    @Override
    Float32 sqrt()
        {
        TODO
        }

    @Override
    Float32 cbrt()
        {
        TODO
        }

    @Override
    Float32 sin()
        {
        TODO
        }

    @Override
    Float32 cos()
        {
        TODO
        }

    @Override
    Float32 tan()
        {
        TODO
        }

    @Override
    Float32 asin()
        {
        TODO
        }

    @Override
    Float32 acos()
        {
        TODO
        }

    @Override
    Float32 atan()
        {
        TODO
        }

    @Override
    Float32 deg2rad()
        {
        TODO
        }

    @Override
    Float32 rad2deg()
        {
        TODO
        }


    // ----- conversions ---------------------------------------------------------------------------

    @Override
    Float32! toFloat32()
        {
        return this;
        }

    @Override
    VarInt toVarInt()
        {
        TODO
        }

    @Override
    VarUInt toVarUInt()
        {
        TODO
        }

    @Override
    VarFloat toVarFloat()
        {
        TODO
        }

    @Override
    VarDec toVarDec()
        {
        TODO
        }
    }
