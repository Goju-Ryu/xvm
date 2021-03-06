import ecstasy.reflect.Parameter;

/**
 * Parameter implementation.
 */
const RTParameter<ParamType>(Int ordinal, String? name, Boolean formal, Boolean hasDefault, ParamType? valDefault)
        implements Parameter<ParamType>
    {
    @Override
    conditional String hasName()
        {
        return name == Null
                ? False
                : (True, name.as(String));
        }

    @Override
    conditional ParamType defaultValue()
        {
        return hasDefault
                ? (True, valDefault.as(ParamType))
                : False;
        }

    // Stringable implementation is a copy from Parameter interface
    @Override
    Int estimateStringLength()
        {
        Int len = ParamType.estimateStringLength();
        if (String name := hasName())
            {
            len += 1 + name.size;
            }
        return len;
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        ParamType.appendTo(buf);
        if (String name := hasName())
            {
            buf.add(' ');
            name.appendTo(buf);
            }
        return buf;
        }
    }