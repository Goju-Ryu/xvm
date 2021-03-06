/**
 * String is a well-known, immutable data type for holding textual information.
 */
const String
        implements UniformIndexed<Int, Char>
        implements Iterable<Char>
        implements Sliceable<Int>
        implements Stringable
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a String from an array of characters.
     *
     * @param chars  an array of characters to include in the String
     */
    construct(Char[] chars)
        {
        this.chars = chars.is(immutable Char[]) ? chars
                   : chars.is(Freezable)        ? chars.freeze()
                   : chars.toArray(Constant).as(immutable Char[]);
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The array of characters that form the content of the String.
     */
    private Char[] chars;


    // ----- operators -----------------------------------------------------------------------------

    /**
     * Duplicate this String the specified number of times.
     *
     * @param n  the number of times to duplicate this String
     *
     * @return a String that is the result of duplicating this String the specified number of times
     */
    @Op("*") String! dup(Int n)
        {
        if (n <= 1)
            {
            return n == 1
                    ? this
                    : "";
            }

        StringBuffer buf = new StringBuffer(size*n);
        for (Int i = 0; i < n; ++i)
            {
            appendTo(buf);
            }
        return buf.toString();
        }

    /**
     * Add the String form of the passed object to this String, returning the result.
     *
     * @param o  the object to render as a String and append to this String
     *
     * @return the concatenation of the String form of the passed object onto this String
     */
    @Op("+") String! append(Object o)
        {
        Int          add = o.is(Stringable) ? o.estimateStringLength() : 0x0F;
        StringBuffer buf = new StringBuffer(size + add);
        return (buf + this + o).toString();
        }


    // ----- operations ----------------------------------------------------------------------------

    /**
     * Obtain a portion of this String, beginning with at specified character index.
     *
     * @param startAt  the index of the first character of the new string
     *
     * @return the specified sub-string
     */
    String! substring(Int startAt)
        {
        return switch()
            {
            case startAt <= 0:   this;
            case startAt < size: this[startAt..size);
            default: "";
            };
        }

    /**
     * Obtain this String, but with its contents reversed.
     *
     * @return the reversed form of this String
     */
    String! reversed()
        {
        return size <= 1
                ? this
                : this[size-1..0];
        }

    /**
     * Strip the whitespace off of the front and back of the string.
     *
     * @return the contents of this String, but without any leading or trailing whitespace
     */
    String trim()
        {
        Int leading = 0;
        val length  = size;
        while (leading < length && this[leading].isWhitespace())
            {
            ++leading;
            }

        if (leading == length)
            {
            return "";
            }

        Int trailing = 0;
        while (this[length-trailing-1].isWhitespace())
            {
            ++trailing;
            }

        return leading == 0 && trailing == 0
                ? this
                : this[leading..size-trailing);
        }

    /**
     * Count the number of occurrences of the specified character in this String.
     *
     * @param value  the character to search for
     *
     * @return the number of times that the specified character occurs in this String
     */
    Int count(Char value)
        {
        Int count = 0;
        for (Char ch : chars)
            {
            if (ch == value)
                {
                ++count;
                }
            }
        return count;
        }

    /**
     * Count the number of occurrences of the specified character in this String.
     *
     * @param value  the character to search for
     *
     * @return the number of times that the specified character occurs in this String
     */
    String![] split(Char separator)
        {
        if (size == 0)
            {
            return [""];
            }

        String[] results = new String[];

        Int start = 0;
        while (Int next := indexOf(separator, start))
            {
            results += start == next ? "" : this[start..next);
            start    = next + 1;
            }

        // whatever remains after the last separator (or the entire String, if there were no
        // separators found)
        results += substring(start);

        return results;
        }

    /**
     * Look for the specified character starting at the specified index.
     *
     * @param value    the character to search for
     * @param startAt  the first index to search from (optional)
     *
     * @return True iff this string contains the character, at or after the `startAt` index
     * @return (conditional) the index at which the specified character was found
     */
    conditional Int indexOf(Char value, Int startAt = 0)
        {
        return chars.indexOf(value, startAt);
        }

    /**
     * Look for the specified character starting at the specified index and searching backwards.
     *
     * @param value    the character to search for
     * @param startAt  the index to start searching backwards from (optional)
     *
     * @return True iff this string contains the character, at or before the `startAt` index
     * @return (conditional) the index at which the specified character was found
     */
    conditional Int lastIndexOf(Char value, Int startAt = Int.maxvalue)
        {
        return chars.lastIndexOf(value, startAt);
        }

    /**
     * Look for the specified `that` starting at the specified index.
     *
     * @param that     the substring to search for
     * @param startAt  the first index to search from (optional)
     *
     * @return a conditional return of the location of the index of the specified substring, or
     *         False if the substring could not be found
     */
     conditional Int indexOf(String! that, Int startAt = 0)
         {
         // if we've already run out of space for that string to fit, then it can't be found
         Int thisLen = this.size;
         Int thatLen = that.size;
         if (startAt > thisLen - thatLen)
             {
             return False;
             }

         // can't start before the start of the string (at zero)
         startAt = startAt.maxOf(0);

         // break out the special conditions (for small search strings)
         if (thatLen <= 1)
             {
             // assume that we can find the empty string wherever we look
             if (thatLen == 0)
                {
                return True, startAt;
                }

             // for single-character strings, use the more efficient single-character search
             return indexOf(that[0], startAt);
             }

         // otherwise, brute force
         Char first = that[0];
         NextTry: while (Int next := indexOf(first, startAt))
             {
             for (Int of = 1; of < thatLen; ++of)
                 {
                 if (this[next+of] != that[of])
                     {
                     startAt = next + 1;
                     continue NextTry;
                     }
                 }
             return True, next;
             }

         return False;
         }

    /**
     * Format this String into a left-justified String of the specified length, with the remainder
     * of the new String filled with the specified character. If the specified length is shorter
     * than the size of this String, then the result will be a truncated copy of this String,
     * containing only the first _length_ characters of this String.
     *
     * @param length  the size of the resulting String
     * @param fill    an optional fill character to use
     *
     * @return this String formatted into a left-justified String filled with the specified
     *         character
     */
    String! leftJustify(Int length, Char fill = ' ')
        {
        switch (length.sign)
            {
            case Negative:
                assert;

            case Zero:
                return "";

            case Positive:
                Int append = length - size;
                switch (append.sign)
                    {
                    case Negative:
                        return this[0..length];

                    case Zero:
                        return this;

                    case Positive:
                        return new StringBuffer(length)
                                .addAll(this)
                                .addAll(fill * append)
                                .toString();
                    }
            }
        }

    /**
     * Format this String into a right-justified String of the specified length, with the remainder
     * of the new String filled with the specified character. If the specified length is shorter
     * than the size of this String, then the result will be a truncated copy of this String,
     * containing only the last _length_ characters of this String.
     *
     * @param length  the size of the resulting String
     * @param fill    an optional fill character to use
     *
     * @return this String formatted into a left-justified String filled with the specified
     *         character
     */
    String! rightJustify(Int length, Char fill = ' ')
        {
        switch (length.sign)
            {
            case Negative:
                assert;

            case Zero:
                return "";

            case Positive:
                Int append = length - size;
                switch (append.sign)
                    {
                    case Negative:
                        return this.substring(-append);

                    case Zero:
                        return this;

                    case Positive:
                        return new StringBuffer(length)
                                .addAll(fill * append)
                                .addAll(this)
                                .toString();
                    }
            }
        }


    // ----- Iterable methods ----------------------------------------------------------------------

    @Override
    Int size.get()
        {
        return chars.size;
        }

    @Override
    Iterator<Char> iterator()
        {
        return chars.iterator();
        }


    // ----- UniformIndexed methods ----------------------------------------------------------------

    @Override
    @Op("[]") Char getElement(Int index)
        {
        return chars[index];
        }


    // ----- Sliceable methods ---------------------------------------------------------------------

    @Override
    @Op("[..]") String slice(Range<Int> indexes)
        {
        return new String(chars[indexes]);
        }


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * @return the characters of this String as an array
     */
    immutable Char[] toCharArray()
        {
        return chars.as(immutable Char[]);
        }

    @Override
    String! toString()
        {
        return this;
        }


    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Determine if the string needs to be escaped in order to be displayed.
     *
     * @return True iff the string should be escaped in order to be displayed
     * @return (conditional) the number of characters in the escaped string
     */
    conditional Int isEscaped()
        {
        Int total = size;
        for (Char ch : chars)
            {
            if (Int n := ch.isEscaped())
                {
                total += n - 1;
                }
            }

        return total == size
                ? False
                : True, total;
        }

    /**
     * Append the string to the Appender, escaping characters as necessary.
     *
     * @param buf  the Appender to append to
     */
    Appender<Char> appendEscaped(Appender<Char> buf)
        {
        for (Char ch : chars)
            {
            ch.appendEscaped(buf);
            }
        return buf;
        }

    /**
     * @return the string as it would appear in source code, in double quotes and escaped as
     *         necessary
     */
    String quoted()
        {
        if (Int len := isEscaped())
            {
            return appendEscaped(new StringBuffer(len + 2).add('\"')).add('\"').toString();
            }
        else
            {
            return new StringBuffer(size+2).add('\"').addAll(this).add('\"').toString();
            }
        }


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return size;
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        return buf.addAll(chars);
        }
    }
