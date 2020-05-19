import io.IllegalUTF;

import numbers.UInt32;

const Char
        implements Sequential
        implements Stringable
        default('\u0000')
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a character from the codepoint indicated by the passed `UInt32` value.
     *
     * @param n  the codepoint for the character
     */
    construct(UInt32 codepoint)
        {
        if (codepoint > 0x10FFFF ||                     // unicode limit
            codepoint > 0xD7FF && codepoint < 0xE000)   // surrogate values are illegal
            {
            throw new IllegalUTF($"illegal code-point: {codepoint}");
            }
        }

    /**
     * Construct a character from the codepoint indicated by the passed UTF8 value.
     *
     * @param utf8  the character, in UTF8 format
     */
    construct(Byte[] utf8)
        {
        Int length = utf8.size;
        UInt32 codepoint;
        switch (length)
            {
            case 1:
                // ASCII value
                codepoint = utf8[0].toUInt32();
                if (codepoint > 0x7F)
                    {
                    throw new IllegalUTF($"Illegal ASCII code in 1-byte UTF8 format: {codepoint}");
                    }
                break;

            case 2..6:
                // #1s first byte  trailing  # trailing  bits  code-points
                // --- ----------  --------  ----------  ----  -----------------------
                //  0  0xxxxxxx    n/a           0         7   U+0000    - U+007F     (ASCII)
                //  2  110xxxxx    10xxxxxx      1        11   U+0080    - U+07FF
                //  3  1110xxxx    10xxxxxx      2        16   U+0800    - U+FFFF
                //  4  11110xxx    10xxxxxx      3        21   U+10000   - U+1FFFFF
                //  5  111110xx    10xxxxxx      4        26   U+200000  - U+3FFFFFF
                //  6  1111110x    10xxxxxx      5        31   U+4000000 - U+7FFFFFFF
                codepoint = utf8[0].toUInt32();
                Int bits = (~codepoint).leftmostBit.trailingZeroCount;
                if (length != 7 - bits)
                    {
                    throw new IllegalUTF($"Expected UTF8 length of {7 - bits} bytes; actual length is {length} bytes");
                    }
                codepoint &= 0b11111 >>> 5 - bits;

                for (Int i : [1..length))
                    {
                    Byte b = utf8[i];
                    if (b & 0b11000000 != 0b10000000)
                        {
                        throw new IllegalUTF("trailing unicode byte does not match 10xxxxxx");
                        }
                    codepoint = codepoint << 6 | (b & 0b00111111).toUInt32();
                    }
                break;

            default:
                throw new IllegalUTF($"Illegal UTF8 encoding length: {length}");
            }

        construct Char(codepoint);
        }

    /**
     * Construct a character from the codepoint indicated by the passed `Byte` value. This is
     * primarily useful for codepoints in the ASCII range.
     *
     * @param codepoint  the codepoint for the character
     */
    construct(Byte codepoint)
        {
        construct Char(codepoint.toUInt32());
        }

    /**
     * Construct a character from the codepoint indicated by the passed `Int` value.
     *
     * @param n  the codepoint for the character
     */
    construct(Int n)
        {
        construct Char(codepoint.toUInt32());
        }


    // ----- properties ----------------------------------------------------------------------------

    UInt32 codepoint;


    // ----- numeric conversion support ------------------------------------------------------------

    conditional Int isDigit()
        {
        Int codepoint = this.codepoint.toInt();
        return codepoint <= 0x39 && codepoint >= 0x30
                ? (True, codepoint - 0x30)
                : False;
        }

    conditional Int isHexit()
        {
        Int codepoint = this.codepoint.toInt();
        return switch (codepoint)
            {
            case 0x30..0x39: (True, codepoint - 0x30);
            case 0x41..0x46: (True, codepoint - 0x41 + 0x0A);
            case 0x61..0x66: (True, codepoint - 0x61 + 0x0A);
            default: False;
            };
        }


    // ----- Sequential ----------------------------------------------------------------------------

    @Override
    conditional Char prev()
        {
        if (codepoint > 0)
            {
            return true, new Char(codepoint - 1);
            }
        return false;
        }

    @Override
    conditional Char next()
        {
        if (codepoint < UInt32.maxvalue)
            {
            return true, new Char(codepoint + 1);
            }
        return false;
        }

    @Override
    Int stepsTo(Char that)
        {
        return that.codepoint.as(Int) - this.codepoint.as(Int);
        }


    // ----- operators ---------------------------------------------------------------------------

    @Op("+")
    Char add(Int n)
        {
        return new Char(this.toInt() + n);
        }

    @Op("-")
    Char sub(Int n)
        {
        return new Char(this.toInt() - n);
        }

    @Op("-")
    Int sub(Char ch)
        {
        return this.toInt() - ch.toInt();
        }

    @Op("*")
    String dup(Int n)
        {
        if (n == 0)
            {
            return "";
            }

        assert n > 0;
        StringBuffer buf = new StringBuffer(n);
        for (Int i = 0; i < n; ++i)
            {
            buf.add(this);
            }
        return buf.toString();
        }


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * A direct conversion from the Char to a Byte is supported because of ASCII. An
     * out-of-range value will result in an exception.
     */
    Byte toByte()
        {
        assert codepoint <= 0x7F;
        return codepoint.toByte();
        }

    /**
     * A conversion to Byte[] results in a byte array with between 1-6 bytes containing
     * a UTF-8 formatted codepoint.
     *
     * Note: The current version 9 of Unicode limits code points to 0x10FFFF, which
     * means that all UTF-8 encoding will use between 1-4 bytes.
     */
    immutable Byte[] utf()
        {
        Int    length = calcUtf8Length();
        Byte[] bytes  = new Byte[length];
        Int    actual = formatUtf8(bytes, 0);
        assert actual == length;
        return bytes.makeImmutable();
        }

    UInt32 toUInt32()
        {
        return codepoint;
        }

    Int toInt()
        {
        return codepoint.toInt();
        }


    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Determine if the specified character is considered to be white-space.
     *
     * @return true iff this character is considered to be an Ecstasy whitespace character
     */
    Boolean isWhitespace()
        {
        // optimize for the ASCII range
        if (codepoint <= 0x7F)
            {
            return codepoint <= 0x20
                && codepoint >= 0x09
                                     // 2               1      0
                                     // 0FEDCBA9876543210FEDCBA9
                && 1 << codepoint-9 & 0b111110100000000000011111 != 0;
            }

        return switch (codepoint)
            {
         // case 0x0009:        //   U+0009      9  HT      Horizontal Tab
         // case 0x000A:        //   U+000A     10  LF      Line Feed
         // case 0x000B:        //   U+000B     11  VT      Vertical Tab
         // case 0x000C:        //   U+000C     12  FF      Form Feed
         // case 0x000D:        //   U+000D     13  CR      Carriage Return
         // case 0x001A:        //   U+001A     26  SUB     End-of-File, or “control-Z”
         // case 0x001C:        //   U+001C     28  FS      File Separator
         // case 0x001D:        //   U+001D     29  GS      Group Separator
         // case 0x001E:        //   U+001E     30  RS      Record Separator
         // case 0x001F:        //   U+001F     31  US      Unit Separator
         // case 0x0020:        //   U+0020     32  SP      Space
            case 0x0085:        //   U+0085    133  NEL     Next Line
            case 0x00A0:        //   U+00A0    160  &nbsp;  Non-breaking space
            case 0x1680:        //   U+1680   5760          Ogham Space Mark
            case 0x2000:        //   U+2000   8192          En Quad
            case 0x2001:        //   U+2001   8193          Em Quad
            case 0x2002:        //   U+2002   8194          En Space
            case 0x2003:        //   U+2003   8195          Em Space
            case 0x2004:        //   U+2004   8196          Three-Per-Em Space
            case 0x2005:        //   U+2005   8197          Four-Per-Em Space
            case 0x2006:        //   U+2006   8198          Six-Per-Em Space
            case 0x2007:        //   U+2007   8199          Figure Space
            case 0x2008:        //   U+2008   8200          Punctuation Space
            case 0x2009:        //   U+2009   8201          Thin Space
            case 0x200A:        //   U+200A   8202          Hair Space
            case 0x2028:        //   U+2028   8232   LS     Line Separator
            case 0x2029:        //   U+2029   8233   PS     Paragraph Separator
            case 0x202F:        //   U+202F   8239          Narrow No-Break Space
            case 0x205F:        //   U+205F   8287          Medium Mathematical Space
            case 0x3000: True;  //   U+3000  12288          Ideographic Space

            default    : False;
            };
        }

    /**
     * Determine if the character acts as a line terminator.
     *
     * @return True iff this character acts as an Ecstasy line terminator
     */
    Boolean isLineTerminator()
        {
        // optimize for the ASCII range
        if (codepoint <= 0x7F)
            {
            // this handles the following cases:
            //   U+000A  10  LF   Line Feed
            //   U+000B  11  VT   Vertical Tab
            //   U+000C  12  FF   Form Feed
            //   U+000D  13  CR   Carriage Return
            return codepoint >= 0x0A && codepoint <= 0x0D;
            }

        // this handles the following cases:
        //   U+0085    133   NEL    Next Line
        //   U+2028   8232   LS     Line Separator
        //   U+2029   8233   PS     Paragraph Separator
        return codepoint == 0x0085 || codepoint == 0x2028 || codepoint == 0x2029;
        }

    /**
     * @return the minimum number of bytes necessary to encode the character in UTF8 format
     */
    Int calcUtf8Length()
        {
        if (codepoint <= 0x7f)
            {
            return 1;
            }

        UInt32 codepoint = this.codepoint >> 11;
        Int    length    = 2;
        while (codepoint != 0)
            {
            codepoint >>= 5;
            ++length;
            }

        return length;
        }

    /**
     * Encode this character into the passed byte array using the UTF8 format.
     *
     * @param bytes  the byte array to write the UTF8 bytes into
     * @param of     the offset into the byte array to write the first byte
     *
     * @return the number of bytes used to encode the character in UTF8 format
     */
    Int formatUtf8(Byte[] bytes, Int of)
        {
        UInt32 ch = codepoint;
        if (ch <= 0x7F)
            {
            // ASCII - single byte 0xxxxxxx format
            bytes[of] = ch.toByte();
            return 1;
            }

        // otherwise the format is based on the number of significant bits:
        // bits  code-points             first byte  trailing  # trailing
        // ----  ----------------------- ----------  --------  ----------
        //  11   U+0080    - U+07FF      110xxxxx    10xxxxxx      1
        //  16   U+0800    - U+FFFF      1110xxxx    10xxxxxx      2
        //  21   U+10000   - U+1FFFFF    11110xxx    10xxxxxx      3
        //  26   U+200000  - U+3FFFFFF   111110xx    10xxxxxx      4
        //  31   U+4000000 - U+7FFFFFFF  1111110x    10xxxxxx      5
        Int trailing;
        switch (ch.leftmostBit)
            {
            case 0b00000000000000000000000010000000:
            case 0b00000000000000000000000100000000:
            case 0b00000000000000000000001000000000:
            case 0b00000000000000000000010000000000:
                bytes[of++] = 0b11000000 | ch >>> 6;
                trailing = 1;
                break;

            case 0b00000000000000000000100000000000:
            case 0b00000000000000000001000000000000:
            case 0b00000000000000000010000000000000:
            case 0b00000000000000000100000000000000:
            case 0b00000000000000001000000000000000:
                bytes[of++] = 0b11100000 | ch >>> 12;
                trailing = 2;
                break;

            case 0b00000000000000010000000000000000:
            case 0b00000000000000100000000000000000:
            case 0b00000000000001000000000000000000:
            case 0b00000000000010000000000000000000:
            case 0b00000000000100000000000000000000:
                bytes[of++] = 0b11110000 | ch >>> 18;
                trailing = 3;
                break;

            case 0b00000000001000000000000000000000:
            case 0b00000000010000000000000000000000:
            case 0b00000000100000000000000000000000:
            case 0b00000001000000000000000000000000:
            case 0b00000010000000000000000000000000:
                bytes[of++] = 0b11111000 | ch >>> 24;
                trailing = 4;
                break;

            case 0b00000100000000000000000000000000:
            case 0b00001000000000000000000000000000:
            case 0b00010000000000000000000000000000:
            case 0b00100000000000000000000000000000:
            case 0b01000000000000000000000000000000:
                bytes[of++] = 0b11111100 | ch >>> 30;
                trailing = 5;
                break;

            default:
                // TODO: ch.toHexString() would be a better output
                throw new IllegalUTF("illegal character: " + ch);
            }
        Int length = trailing + 1;

        // write out trailing bytes; each has the same "10xxxxxx" format with 6
        // bits of data
        while (trailing > 0)
            {
            bytes[of++] = 0b10_000000 | (ch >>> --trailing * 6 & 0b00_111111);
            }

        return length;
        }

    /**
     * Determine if the character needs to be escaped in order to be displayed.
     *
     * @return true iff the character should be escaped in order to be displayed
     * @return (conditional) the number of characters in the escape sequence
     */
    conditional Int isEscaped()
        {
        return switch (codepoint)
            {
            case 0x00           :               // null terminator
            case 0x08           :               // backspace
            case 0x09           :               // horizontal tab
            case 0x0A           :               // line feed
            case 0x0B           :               // vertical tab
            case 0x0C           :               // form feed
            case 0x0D           :               // carriage return
            case 0x1A           :               // EOF
            case 0x1B           :               // escape
            case 0x22           :               // double quotes
            case 0x27           :               // single quotes
            case 0x5C           :               // the escaping slash itself requires an explicit escape
            case 0x7F           : (True, 2);    // DEL

            case 0x00..0x1F     :               // C0 control characters
            case 0x80..0x9F     :               // C1 control characters
            case 0x2028..0x2029 : (True, 5);    // line and paragraph separator

            default             : False;
            };
        }

    /**
     * Append the specified character to the StringBuilder, escaping if
     * necessary.
     *
     * @param sb  the StringBuilder to append to
     * @param ch  the character to escape
     *
     * @return the StringBuilder
     */
    void appendEscaped(Appender<Char> appender)
        {
        switch (codepoint)
            {
            case 0x00:
                // null terminator
                appender.add('\\')
                        .add('0');
                break;

            case 0x08:
                // backspace
                appender.add('\\')
                        .add('b');
                break;

            case 0x09:
                // horizontal tab
                appender.add('\\')
                        .add('t');
                break;

            case 0x0A:
                // line feed
                appender.add('\\')
                        .add('n');
                break;

            case 0x0B:
                // vertical tab
                appender.add('\\')
                        .add('v');
                break;

            case 0x0C:
                // form feed
                appender.add('\\')
                        .add('f');
                break;

            case 0x0D:
                // carriage return
                appender.add('\\')
                        .add('r');
                break;

            case 0x1A:
                // EOF
                appender.add('\\')
                        .add('z');
                break;

            case 0x1B:
                // escape
                appender.add('\\')
                        .add('e');
                break;

            case 0x22:
                // double quotes
                appender.add('\\')
                        .add('\"');
                break;

            case 0x27:
                // single quotes
                appender.add('\\')
                        .add('\'');
                break;

            case 0x5C:
                // the escaping slash itself requires an explicit escape
                appender.add('\\')
                        .add('\\');
                break;

            case 0x7F:
               // DEL
                appender.add('\\')
                        .add('d');
               break;

            case 0x00..0x1F     :       // C0 control characters
            case 0x80..0x9F     :       // C1 control characters
            case 0x2028..0x2029 :       // line and paragraph separator
                appender.add('\\')
                        .add('u')
                        .add((codepoint & 0xF000 >>> 24).toHexit())
                        .add((codepoint & 0x0F00 >>> 16).toHexit())
                        .add((codepoint & 0x00F0 >>>  8).toHexit())
                        .add((codepoint & 0x000F >>>  0).toHexit());
                break;

            default:
                appender.add(this);
                break;
            }
        }


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return 1;
        }

    @Override
    void appendTo(Appender<Char> appender)
        {
        appender.add(this);
        }
    }