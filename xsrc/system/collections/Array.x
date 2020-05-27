import numbers.Bit;
import numbers.BFloat16;
import numbers.Dec32;
import numbers.Dec64;
import numbers.Dec128;
import numbers.Float16;
import numbers.Float32;
import numbers.Float64;
import numbers.Float128;
import numbers.Int8;
import numbers.Int16;
import numbers.Int32;
import numbers.Int64;
import numbers.Int128;
import numbers.Nibble;
import numbers.UInt8;
import numbers.UInt16;
import numbers.UInt32;
import numbers.UInt64;
import numbers.UInt128;
import numbers.VarDec;
import numbers.VarFloat;
import numbers.VarInt;
import numbers.VarUInt;

/**
 * Array is an implementation of List, an Int-indexed container of elements of a particular type.
 *
 * Array implements all four VariablyMutable forms: Mutable, Fixed, Persistent, and Constant.
 * To construct an Array with a specific form of mutability, use the
 * [construct(Mutability, Element...)] constructor.
 */
class Array<Element>
        implements List<Element>
        implements MutableAble, FixedSizeAble, PersistentAble, ImmutableAble
        implements Stringable
        incorporates text.Stringer
        incorporates conditional BitArray<Element extends Bit>
        incorporates conditional ByteArray<Element extends Byte>
        incorporates conditional Orderer<Element extends Orderable>
        incorporates conditional Hasher<Element extends Hashable>
        // TODO have to implement Const (at least conditionally if Element extends Const)
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a dynamically growing array with the specified initial capacity.
     *
     * @param capacity  the suggested initial capacity; since the Array will grow as necessary, this
     *                  is not required, but specifying it when the expected size of the Array is
     *                  known allows the Array to pre-size itself, which can reduce the inefficiency
     *                  related to resizing
     */
    construct(Int capacity = 0)
        {
        if (capacity < 0)
            {
            throw new IllegalArgument("capacity (" + capacity + ") must be >= 0");
            }
        }
    finally
        {
        if (capacity > 0)
            {
            ElementImpl cur = new ElementImpl();
            while (--capacity > 0)
                {
                cur = new ElementImpl(cur);
                }
            head = cur;
            }

        this.mutability = Mutable;
        }

    /**
     * Construct a fixed size array with the specified size and initial value.
     *
     * @param size    the size of the fixed size array
     * @param supply  the value or the supply function for initializing the elements of the array
     */
    construct(Int size, Element | function Element (Int) supply)
        {
        }
    finally
        {
        if (size > 0)
            {
            function Element (Int) valueFor = supply.is(Element) ? (_) -> supply : supply;

            ElementImpl cur = new ElementImpl(valueFor(0));
            head = cur;

            if (size > 1)
                {
                for (Int i : [1..size))
                    {
                    ElementImpl next = new ElementImpl(valueFor(i));
                    cur.next = next;
                    cur      = next;
                    }
                }
            }

        this.mutability = Fixed;
        }

    /**
     * Construct a fixed size array with the specified size and initial value.
     *
     * @param mutability  the mutability setting for the array
     * @param elements    the elements to use to initialize the contents of the array
     */
    construct(Mutability mutability, Element... elements)
        {
        }
    finally
        {
        Int size = elements.size;
        if (size > 0)
            {
            function Element (Element) transform = mutability == Constant
                    ? e -> (e.is(Const) ? e : e.is(ImmutableAble) ? e.ensureImmutable() : assert)
                    : e -> e;

            Int         index = size - 1;
            ElementImpl cur   = new ElementImpl(transform(elements[index]));
            while (--index >= 0)
                {
                cur = new ElementImpl(transform(elements[index]), cur);
                }
            head = cur;
            }

        this.mutability = mutability;
        if (mutability == Constant)
            {
            makeImmutable();
            }
        }

    /**
     * Construct a slice of another array.
     *
     * @param array    the array that this slice delegates to (which could itself be a slice)
     * @param section  the interval that defines the section of the array that the slice represents
     */
    construct(Array<Element> array, Interval<Int> section)  // TODO CP add support for inclusive/exclusive
        {
        ArrayDelegate<Element> delegate = new ArrayDelegate<Element>()
            {
            @Override
            Int size.get()
                {
                return section.size;
                }

            @Override
            Mutability mutability.get()
                {
                return array.mutability;
                }

            @Override
            @Op("[]")
            Element getElement(Int index)
                {
                return array[translateIndex(index)];
                }

            @Override
            @Op("[]=")
            void setElement(Int index, Element value)
                {
                array[translateIndex(index)] = value;
                }

            @Override
            Var<Element> elementAt(Int index)
                {
                return array.elementAt(translateIndex(index));
                }

            /**
             * Translate from an index into a slice, into an index into the underlying array.
             *
             * @param index  the index into the slice
             *
             * @return the corresponding index into the underlying array
             */
            private Int translateIndex(Int index)
                {
                assert:bounds index >= 0 && index < section.size;
                return section.reversed
                        ? section.effectiveUpperBound - index
                        : section.effectiveLowerBound + index;
                }
            };

        construct Array(delegate);
        }

    /**
     * Construct an array that delegates to some other data structure (such as an array).
     *
     * @param delegate  an ArrayDelegate object that allows this array to delegate its functionality
     */
    protected construct(ArrayDelegate<Element> delegate)
        {
        this.delegate = delegate;
        }
    finally
        {
        if (mutability == Constant)
            {
            makeImmutable();
            }
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The capacity of an array is the amount that the array can hold without resizing.
     */
    Int capacity
        {
        @Override
        Int get()
            {
            if (delegate != null)
                {
                return delegate?.size : assert; // TODO assumptions implementation (should not need '?')
                }

            Int count = 0;
            for (ElementImpl? cur = head; cur != null; cur = cur.next)
                {
                ++count;
                }
            return count;
            }

        @Override
        void set(Int newCap)
            {
            assert delegate == null;

            Int oldCap = get();
            if (newCap == oldCap)
                {
                return;
                }

            assert newCap >= 0;
            assert newCap >= size;
            assert mutability == Mutable;

            ElementImpl cur = new ElementImpl();
            while (--capacity > 0)
                {
                cur = new ElementImpl(cur);
                }

            if (head == null)
                {
                head = cur;
                }
            else
                {
                tail?.next = cur;
                }
            }
        }


    // ----- operations ----------------------------------------------------------------------------

    /**
     * Fill the specified elements of this array with the specified value.
     *
     * @param value     the value to use to fill the array
     * @param interval  an optional interval of element indexes, defaulting to the entire array
     *
     * @throws ReadOnly  if the array mutability is not Mutable or Fixed
     */
    Array fill(Element value, Interval<Int>? interval = Null)
        {
        if (interval == Null)
            {
            if (empty)
                {
                return this;
                }
            interval = [0..size);
            }

        if (mutability.persistent)
            {
            Array result = new Array<Element>(size, i -> (interval.contains(i) ? value : this[i]));
            return mutability == Constant
                    ? result.ensureImmutable(true)
                    : result.ensurePersistent(true);
            }
        else
            {
            for (Int i : interval)
                {
                this[i] = value;
                }
            return this;
            }
        }


    // ----- VariablyMutable interface -------------------------------------------------------------

    @Override
    public/private Mutability mutability.get()
        {
        if (delegate != null)
            {
            Mutability mutability = delegate?.mutability : assert; // TODO
            return mutability == Mutable ? Fixed : mutability;
            }

        return super();
        }

    @Override
    Array ensureMutable()
        {
        return mutability == Mutable
                ? this
                : new Array(Mutable, this);
        }

    /**
     * Return a fixed-size array (whose values are mutable) of the same type and with the same
     * contents as this array. If this array is already a fixed-size array, then _this_ is returned.
     */
    @Override
    Array ensureFixedSize(Boolean inPlace = False)
        {
        if (inPlace && mutability == Mutable || mutability == Fixed)
            {
            mutability = Fixed;
            return this;
            }

        return new Array(Fixed, this);
        }

    /**
     * Return a persistent array of the same element types and values as are present in this array.
     * If this array is already persistent or `const`, then _this_ is returned.
     *
     * A _persistent_ array does not support replacing the contents of the elements in this array
     * using the {@link replace} method; instead, calls to {@link replace} will return a new array.
     */
    @Override
    Array ensurePersistent(Boolean inPlace = False)
        {
        if (delegate == null && inPlace && !mutability.persistent || mutability == Persistent)
            {
            mutability = Persistent;
            return this;
            }

        return new Array(Persistent, this);
        }

    /**
     * Return a `const` array of the same type and contents as this array.
     *
     * All mutating calls to a `const` array will result in the creation of a new
     * `const` array with the requested changes incorporated.
     *
     * @throws Exception if any of the values in the array are not `const` and are not
     *         {@link ImmutableAble}
     */
    @Override
    immutable Array ensureImmutable(Boolean inPlace = False)
        {
        if (mutability == Constant)
            {
            // it is possible, in the case of a delegating array, that the underling array has been
            // transitioned to constant without this array having done so as well
            if (delegate != null && !this.is(immutable Object))
                {
                makeImmutable();
                }

            return this.as(immutable Element[]);
            }

        if (!inPlace || delegate != null)
            {
            return new Array(Constant, this).as(immutable Element[]);
            }

        // all elements must be immutable or ImmutableAble
        Boolean convert = False;
        loop: for (Element element : this)
            {
            if (!element.is(immutable Object))
                {
                if (element.is(ImmutableAble))
                    {
                    convert = True;
                    }
                else
                    {
                    throw new ConstantRequired("[" + loop.count + "]");
                    }
                }
            }

        if (convert)
            {
            loop: for (Element element : this)
                {
                if (!element.is(immutable Object))
                    {
                    assert element.is(ImmutableAble);
                    this[loop.count] = element.ensureImmutable(True);
                    }
                }
            }

        // the "mutability" property has to be set before calling makeImmutable(), since no changes
        // will be possible afterwards
        Mutability prev = mutability;
        if (this:struct.isMutable())
            {
            mutability = Constant;
            }

        try
            {
            return makeImmutable();
            }
        catch (Exception e)
            {
            if (this:struct.isMutable())
                {
                mutability = prev;
                }
            throw e;
            }
        }


    // ----- UniformIndexed interface --------------------------------------------------------------

    @Override
    @Op("[]")
    Element getElement(Int index)
        {
        return elementAt(index).get();
        }

    @Override
    @Op("[]=")
    void setElement(Int index, Element value)
        {
        if (mutability.persistent)
            {
            throw new ReadOnly();
            }
        elementAt(index).set(value);
        }

    @Override
    Var<Element> elementAt(Int index)
        {
        if (delegate != null)
            {
            return delegate?.elementAt(index) : assert; // TODO shouldn't need null check
            }

        if (index < 0 || index >= size)
            {
            throw new OutOfBounds("index=" + index + ", size=" + size);
            }

        ElementImpl element = head.as(ElementImpl);
        while (index-- > 0)
            {
            element = element.next.as(ElementImpl);
            }

        return element;
        }


    // ----- Sequence interface --------------------------------------------------------------------

    @Override
    Int size.get()
        {
        if (delegate != null)
            {
            return delegate?.size : assert; // TODO
            }

        Int          count = 0;
        ElementImpl? cur   = head;
        while (cur?.valueRef.assigned)
            {
            ++count;
            }
        return count;
        }

    @Override
    @Op("[..]") Array slice(Range<Int> indexes)
        {
        if (indexes.effectiveFirst == 0 && indexes.size == this.size)
            {
            return this;
            }

        Array<Element> result = new Array(this, indexes);

        // a slice of an immutable array is also immutable
        return this.is(immutable Object)
                ? result.makeImmutable()
                : result;
        }

    @Override
    Array reify()
        {
        return delegate == null
                ? this
                : new Array(mutability, this);
        }


    // ----- Collection interface ------------------------------------------------------------------

    @Override
    Boolean contains(Element value)
        {
        // use the default implementation from the Sequence interface
        return indexOf(value);
        }

    @Override
    Element[] toArray(VariablyMutable.Mutability? mutability = Null)
        {
        return mutability == null || mutability == this.mutability
                ? this
                : new Array(mutability, this);  // create a copy of the desired mutability
        }

    @Override
    @Op("+")
    Array add(Element element)
        {
        switch (mutability)
            {
            case Mutable:
                ElementImpl el = new ElementImpl(element);
                if (head == null)
                    {
                    head = el;
                    }
                else
                    {
                    tail?.next = el;
                    }
                return this;

            case Fixed:
                throw new ReadOnly();

            case Persistent:
            case Constant:
                Array result = new Array<Element>(size + 1, i -> (i < size ? this[i] : element));
                return mutability == Persistent
                        ? result.ensurePersistent(true)
                        : result.ensureImmutable(true);
            }
        }

    @Override
    @Op("+")
    Array addAll(Iterable<Element> values)
        {
        switch (mutability)
            {
            case Mutable:
                for (Element value : values)
                    {
                    add(value);
                    }
                return this;

            case Fixed:
                throw new ReadOnly();

            case Persistent:
            case Constant:
                Iterator<Element> iter = values.iterator();
                function Element (Int) supply = i ->
                    {
                    if (i < size)
                        {
                        return this[i];
                        }
                    assert Element value := iter.next();
                    return value;
                    };
                Element[] result = new Array<Element>(this.size + values.size, supply);
                return mutability == Persistent
                        ? result.ensurePersistent(true)
                        : result.ensureImmutable(true);
            }
        }

    @Override
    (Array, Int) removeIf(function Boolean (Element) shouldRemove)
        {
        Int[]? indexes = null;
        loop: for (Element value : this)
            {
            if (shouldRemove(value))
                {
                indexes = (indexes ?: new Int[]) + loop.count;
                }
            }

        if (indexes == null)
            {
            return this, 0;
            }

        if (indexes.size == 1)
            {
            return delete(indexes[0]), 1;
            }

        // copy everything except the "shouldRemove" elements to a new array
        Int            newSize = size - indexes.size;
        Array<Element> result  = new Array(newSize);
        Int            delete  = indexes[0];
        Int            next    = 1;
        for (Int index = 0; index < size; ++index)
            {
            if (index == delete)
                {
                delete = next < indexes.size ? indexes[next++] : Int.maxvalue;
                }
            else
                {
                result += this[index];
                }
            }

        return switch (mutability)
            {
            case Mutable   : result;
            case Fixed     : result.ensureFixedSize (True);
            case Persistent: result.ensurePersistent(True);
            case Constant  : result.ensureImmutable (True);
            }, indexes.size;
        }

    @Override
    Array clear()
        {
        if (empty)
            {
            return this;
            }

        switch (mutability)
            {
            case Mutable:
                head = Null;
                return this;

            case Fixed:
                throw new ReadOnly();

            case Persistent:
            case Constant:
                return new Array<Element>(mutability, []);
            }
        }


    // ----- List interface ------------------------------------------------------------------------

    @Override
    Array replace(Int index, Element value)
        {
        if (mutability.persistent)
            {
            Element[] result = new Array(size, i -> (i == index ? value : this[i]));
            return mutability == Persistent
                    ? result.ensurePersistent(true)
                    : result.ensureImmutable(true);
            }
        else
            {
            this[index] = value;
            return this;
            }
        }

    @Override
    Array insert(Int index, Element value)
        {
        if (index == size)
            {
            return this + value;
            }

        switch (mutability)
            {
            case Mutable:
                ElementImpl node = elementAt(index).as(ElementImpl);
                node.next  = new ElementImpl(node.value, node.next);
                node.value = value;
                return this;

            case Fixed:
                throw new ReadOnly();

            case Persistent:
            case Constant:
                Element[] result = new Array(size + 1,
                        i -> switch (i <=> index)
                            {
                            case Lesser : this[i];
                            case Equal  : value;
                            case Greater: this[i-1];
                            });
                return mutability == Persistent
                        ? result.ensurePersistent(true)
                        : result.ensureImmutable(true);
            }
        }

    @Override
    Array insertAll(Int index, Iterable<Element> values)
        {
        if (values.size == 0)
            {
            return this;
            }

        if (values.size == 1)
            {
            assert Element value := values.iterator().next();
            return insert(index, value);
            }

        if (index == size)
            {
            return this + values;
            }

        if (index < 0 || index >= size)
            {
            throw new OutOfBounds("index=" + index + ", size=" + size);
            }

        switch (mutability)
            {
            case Mutable:
                Iterator<Element> iter = values.iterator();
                assert Element value := iter.next();
                ElementImpl  first = new ElementImpl(value);
                ElementImpl  last  = first;
                ElementImpl? head  = this.head;
                while (value := iter.next())
                    {
                    last.next = new ElementImpl(value);
                    }
                if (index == 0)
                    {
                    if (head == null)
                        {
                        head = first;
                        }
                    else
                        {
                        last.next = head.next;
                        head      = first;
                        }
                    }
                else
                    {
                    ElementImpl node = elementAt(index-1).as(ElementImpl);
                    last.next = node.next;
                    node.next = first;
                    }
                this.head = head;
                return this;

            case Fixed:
                throw new ReadOnly();

            case Persistent:
            case Constant:
                Iterator<Element> iter  = values.iterator();
                Int               wedge = values.size;
                function Element (Int) supply = i ->
                    {
                    if (i < index)
                        {
                        return this[i];
                        }
                    else if (i < index + wedge)
                        {
                        assert Element value := iter.next();
                        return value;
                        }
                    else
                        {
                        return this[i-wedge];
                        }
                    };
                Element[] result = new Array<Element>(this.size + values.size, supply);
                return mutability == Persistent
                        ? result.ensurePersistent(true)
                        : result.ensureImmutable(true);
            }
        }

    @Override
    Array delete(Int index)
        {
        if (index < 0 || index >= size)
            {
            throw new OutOfBounds("index=" + index + ", size=" + size);
            }

        switch (mutability)
            {
            case Mutable:
                if (index == 0)
                    {
                    head = head.as(ElementImpl).next;
                    }
                else
                    {
                    ElementImpl node = elementAt(index-1).as(ElementImpl);
                    node.next = node.next.as(ElementImpl).next;
                    }
                return this;

            case Fixed:
                throw new ReadOnly();

            case Persistent:
            case Constant:
                Element[] result = new Array(size, i -> this[i < index ? i : i+1]);
                return mutability == Persistent
                        ? result.ensurePersistent(true)
                        : result.ensureImmutable(true);
            }
        }

    @Override
    Array delete(Interval<Int> interval)
        {
        Int lo = interval.lowerBound;
        Int hi = interval.upperBound;
        if (lo < 0 || hi >= size)
            {
            throw new OutOfBounds("interval=" + interval + ", size=" + size);
            }

        if (lo == hi)
            {
            return delete(lo);
            }

        if (lo == 0 && hi == size-1)
            {
            return clear();
            }

        switch (mutability)
            {
            case Mutable:
                if (lo == 0)
                    {
                    head = elementAt(hi+1).as(ElementImpl);
                    }
                else
                    {
                    elementAt(lo-1).as(ElementImpl).next = (hi == size-1)
                            ? null
                            : elementAt(hi+1).as(ElementImpl);
                    }
                return this;

            case Fixed:
                throw new ReadOnly();

            case Persistent:
            case Constant:
                Int       gap    = interval.size;
                Element[] result = new Array(size, i -> this[i < lo ? i : i+gap]);
                return mutability == Persistent
                        ? result.ensurePersistent(true)
                        : result.ensureImmutable(true);
            }
        }


    // ----- internal implementation details -------------------------------------------------------

    /**
     * Linked list head.
     */
    private ElementImpl? head;

    /**
     * Linked list tail.
     */
    private ElementImpl? tail.get()
        {
        ElementImpl? head = this.head;
        if (head == Null)
            {
            return Null;
            }

        ElementImpl cur = head;
        while (True)
            {
            ElementImpl? next = cur.next;
            if (next == Null)
                {
                return cur;
                }
            cur = next;
            }
        }

    /**
     * A node in the linked list.
     */
    private class ElementImpl
            delegates Var<Element>(valueRef)
        {
        /**
         * Construct an empty element.
         *
         * @param next   the next element in the array (optional)
         */
        construct(ElementImpl? next = null)
            {
            this.next = next;
            }

        /**
         * Construct an initialized element.
         *
         * @param value  the initial value for the element
         * @param next   the next element in the array (optional)
         */
        construct(Element value, ElementImpl? next = null)
            {
            this.value = value;
            this.next  = next;
            }

        /**
         * The value stored in the element.
         */
        @Unassigned
        Element value.set(Element value)
            {
            if (this.Array.mutability.persistent)
                {
                throw new ReadOnly();
                }
            super(value);
            }

        /**
         * The next element in the linked list.
         */
        ElementImpl? next = null;

        /**
         * The reference to the storage for the `value` property.
         */
        Var<Element> valueRef.get()
            {
            return &value;
            }
        }

    /**
     * An interface to which an Array can delegate its operations, iff the array is simply a
     * representation of some other structure (such as another array).
     */
    protected static interface ArrayDelegate<Element>
            extends UniformIndexed<Int, Element>
            extends VariablyMutable
        {
        @RO Int size;
        }

    /**
     * If the array is simply a representation of some other structure (such as another array), then
     * this is the object to which this Array will delegate its operations.
     */
    private ArrayDelegate<Element>? delegate;


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        Int capacity = 2; // allow for "[]"
        if (Element.is(Type<Stringable>))
            {
            for (Element v : this)
                {
                capacity += v.estimateStringLength() + 2; // allow for ", "
                }
            }
        else
            {
            capacity += 10 * size;
            }

        return capacity;
        }

    @Override
    void appendTo(Appender<Char> appender)
        {
        appender.add('[');

        if (Element.is(Type<Stringable>))
            {
            loop: for (Element v : this)
                {
                if (!loop.first)
                    {
                    appender.add(", ");
                    }

                v.appendTo(appender);
                }
            }
        else
            {
            loop: for (Element v : this)
                {
                if (!loop.first)
                    {
                    appender.add(", ");
                    }

                if (v.is(Stringable))
                    {
                    v.appendTo(appender);
                    }
                else
                    {
                    v.toString().appendTo(appender);
                    }
                }
            }

        appender.add(']');
        }


    // ----- BitArray mixin ------------------------------------------------------------------------

    /**
     * Functionality specific to arrays of bits.
     */
    static mixin BitArray<Element extends Bit>
            into Array<Element>
        {
        // REVIEW CP+GG &= etc. for mutable bit array
        // REVIEW CP+GG mutability of returned arrays

        /**
         * Bitwise AND.
         */
        @Op("&")
        Bit[] and(Bit[] that)
            {
            assert:bounds this.size == that.size;
            return new Array<Bit>(size, i -> this[i] & that[i]);
            }

        /**
         * Bitwise OR.
         */
        @Op("|")
        Bit[] or(Bit[] that)
            {
            assert:bounds this.size == that.size;
            return new Array<Bit>(size, i -> this[i] | that[i]);
            }

        /**
         * Bitwise XOR.
         */
        @Op("^")
        Bit[] xor(Bit[] that)
            {
            assert:bounds this.size == that.size;
            return new Array<Bit>(size, i -> this[i] ^ that[i]);
            }

        /**
         * Bitwise NOT.
         */
        @Op("~")
        Bit[] not()
            {
            return new Array<Bit>(size, i -> ~this[i]);
            }

        /**
         * Shift bits left. This is both a logical left shift and arithmetic left shift, for
         * both signed and unsigned integer values.
         */
        @Op("<<")
        Bit[] shiftLeft(Int count)
            {
            return new Array<Bit>(size, i -> (i < size-count ? this[i + count] : 0));
            }

        /**
         * Shift bits right. For signed integer values, this is an arithmetic right shift. For
         * unsigned integer values, this is both a logical right shift and arithmetic right
         * shift.
         */
        @Op(">>")
        Bit[] shiftRight(Int count)
            {
            return new Array<Bit>(size, i -> (i < count ? this[0] : this[i - count]));
            }

        /**
         * "Unsigned" shift bits right. For signed integer values, this is an logical right
         * shift. For unsigned integer values, this is both a logical right shift and arithmetic
         * right shift.
         */
        @Op(">>>")
        Bit[] shiftAllRight(Int count)
            {
            return new Array<Bit>(size, i -> (i < count ? 0 : this[i - count]));
            }

        /**
         * Rotate bits left.
         */
        Bit[] rotateLeft(Int count)
            {
            return new Array<Bit>(size, i -> this[(i + count) % size]);
            }

        /**
         * Rotate bits right.
         */
        Bit[] rotateRight(Int count)
            {
            return new Array<Bit>(size, i -> this[(i - count) % size]);
            }


        // ----- TODO

        /**
         * @return a new bit array that is an "add" of the specified bit arrays.
         */
        static Bit[] bitAdd(Bit[] bits1, Bit[] bits2)
            {
            Int bitLength = bits1.size;
            assert bits2.size == bitLength;

            Bit[] bitsNew = new Bit[bitLength];
            Bit carry = 0;
            for (Int i = 0; i < bitLength; ++i)
                {
                Bit aXorB = bits1[i] ^ bits2[i];
                Bit aAndB = bits1[i] & bits2[i];

                bitsNew[i] = aXorB ^ carry;
                carry = (aXorB & carry) | aAndB;
                }
            if (carry == 1)
                {
                throw new OutOfBounds();
                }
            return bitsNew;
            }


        // ----- conversions -----------------------------------------------------------------------

        /**
         * @return an array of booleans corresponding to the bits in this array
         */
        immutable Boolean[] toBooleanArray()
            {
            return new Array<Boolean>(size, i -> this[i].toBoolean()).ensureImmutable(true);
            }

        /**
         * @return an array of nibbles corresponding to the bits in this array
         */
        immutable Nibble[] toNibbleArray()
            {
            Int      nibcount = (size+3) / 4;
            Nibble[] nibbles  = new Nibble[nibcount];
            Int      nibnum   = 0;
            Int      bitnum   = -((4 - size % 4) % 4);

            if (bitnum < 0)
                {
                // not enough bits to make a full nibble, so assume that the missing bits are 0s
                nibbles[0] = switch (bitnum)
                    {
                    case -3: new Nibble([this[1], 0      , 0      , 0].as(Bit[]));
                    case -2: new Nibble([this[2], this[1], 0      , 0].as(Bit[]));
                    case -1: new Nibble([this[3], this[2], this[1], 0].as(Bit[]));
                    default: assert;
                    };

                ++nibnum;
                bitnum += 4;
                }

            while (nibnum < nibcount)
                {
                nibbles[nibnum++] = new Nibble(this[bitnum+3..bitnum]);
                bitnum += 4;
                }

            return nibbles.ensureImmutable(true);
            }

        /**
         * Obtain the number as an array of bytes, in left-to-right order.
         */
        immutable Byte[] toByteArray()
            {
            TODO CP
//            // make sure the bit length is at least 8, and also a power-of-two
//            assert bitLength == (bitLength & ~0x7).leftmostBit;
//
//            // not used
//            class SequenceImpl(Number num)
//                    implements Sequence<Byte>
//                {
//                @Override
//                @RO Int size.get()
//                    {
//                    return num.bitLength / 8;
//                    }
//
//                @Override
//                Byte getElement(Int index)
//                    {
//                    assert index >= 0 && index < size;
//
//                    // the byte array is in the opposite (!!!) sequence of the bit array; bit 0 is
//                    // the least significant (rightmost) bit, while byte 0 is the leftmost byte
//                    Bit[] bits = num.toBitArray();
//                    Int   of   = bits.size - index * 8 - 1;
//                    return new Byte([bits[of], bits[of-1], bits[of-2], bits[of-3],
//                             bits[of-4], bits[of-5], bits[of-6], bits[of-7]].as(Bit[]));
//                    }
//                }
//
//            Bit[]  bits  = toBitArray();
//            Int    size  = byteLength;
//            Byte[] bytes = new Byte[size];
//
//            for (Int index : 0..size-1)
//                {
//                // the byte array is in the opposite (!!!) sequence of the bit array; bit 0 is
//                // the least significant (rightmost) bit, while byte 0 is the leftmost byte
//                Int of = bitLength - index * 8 - 1;
//                bytes[index] = new Byte([bits[of], bits[of-1], bits[of-2], bits[of-3],
//                         bits[of-4], bits[of-5], bits[of-6], bits[of-7]].as(Bit[]));
//                }
//
//            return bytes.ensureImmutable(true);
            }

        /**
         * Convert the bit array to a variable-length signed integer.
         */
        VarInt toVarInt()
            {
            TODO
            }

        /**
         * Convert the bit array to a signed 8-bit integer.
         *
         * @throws OutOfBounds  if the resulting value is out of the signed 8-bit integer range
         */
        Int8 toInt8()
            {
            return toVarInt().toInt8();
            }

        /**
         * Convert the bit array to a signed 16-bit integer.
         *
         * @throws OutOfBounds  if the resulting value is out of the signed 16-bit integer range
         */
        Int16 toInt16()
            {
            return toVarInt().toInt16();
            }

        /**
         * Convert the bit array to a signed 32-bit integer.
         *
         * @throws OutOfBounds  if the resulting value is out of the signed 32-bit integer range
         */
        Int32 toInt32()
            {
            return toVarInt().toInt32();
            }

        /**
         * Convert the bit array to a signed 64-bit integer.
         *
         * @throws OutOfBounds  if the resulting value is out of the signed 64-bit integer range
         */
        Int64 toInt()
            {
            return toVarInt().toInt();
            }

        /**
         * Convert the bit array to a signed 128-bit integer.
         *
         * @throws OutOfBounds  if the resulting value is out of the signed 128-bit integer range
         */
        Int128 toInt128()
            {
            return toVarInt().toInt128();
            }

        /**
         * Convert the bit array to a variable-length unsigned integer.
         */
        VarUInt toVarUInt()
            {
            TODO
            }

        /**
         * Convert the bit array to a unsigned 8-bit integer.
         *
         * @throws OutOfBounds  if the resulting value is out of the unsigned 8-bit integer range
         */
        UInt8 toByte()
            {
            return toVarUInt().toByte();
            }

        /**
         * Convert the bit array to a unsigned 16-bit integer.
         *
         * @throws OutOfBounds  if the resulting value is out of the unsigned 16-bit integer range
         */
        UInt16 toUInt16()
            {
            return toVarUInt().toUInt16();
            }

        /**
         * Convert the bit array to a unsigned 32-bit integer.
         *
         * @throws OutOfBounds  if the resulting value is out of the unsigned 32-bit integer range
         */
        UInt32 toUInt32()
            {
            return toVarUInt().toUInt32();
            }

        /**
         * Convert the bit array to a unsigned 64-bit integer.
         *
         * @throws OutOfBounds  if the resulting value is out of the unsigned 64-bit integer range
         */
        UInt64 toUInt()
            {
            return toVarUInt().toUInt();
            }

        /**
         * Convert the bit array to a unsigned 128-bit integer.
         *
         * @throws OutOfBounds  if the resulting value is out of the unsigned 128-bit integer range
         */
        UInt128 toUInt128()
            {
            return toVarInt().toUInt128();
            }

        /**
         * Convert the bit array to a variable-length binary radix floating point number.
         */
        VarFloat toVarFloat()
            {
            TODO
            }

        /**
         * Convert the bit array to a 16-bit radix-2 (binary) floating point number.
         */
        Float16 toFloat16()
            {
            return toVarFloat().toFloat16();
            }

        /**
         * Convert the bit array to a 16-bit radix-2 (binary) floating point number.
         */
        BFloat16 toBFloat16()
            {
            return toVarFloat().toBFloat16();
            }

        /**
         * Convert the bit array to a 32-bit radix-2 (binary) floating point number.
         */
        Float32 toFloat32()
            {
            return toVarFloat().toFloat32();
            }

        /**
         * Convert the bit array to a 64-bit radix-2 (binary) floating point number.
         */
        Float64 toFloat64()
            {
            return toVarFloat().toFloat64();
            }

        /**
         * Convert the bit array to a 128-bit radix-2 (binary) floating point number.
         */
        Float128 toFloat128()
            {
            return toVarFloat().toFloat128();
            }

        /**
         * Convert the bit array to a variable-length decimal radix floating point number.
         */
        VarDec toVarDec()
            {
            TODO
            }

        /**
         * Convert the bit array to a 32-bit radix-10 (decimal) floating point number.
         */
        Dec32 toDec32()
            {
            return toVarDec().toDec32();
            }

        /**
         * Convert the bit array to a 64-bit radix-10 (decimal) floating point number.
         */
        Dec64 toDec64()
            {
            return toVarDec().toDec64();
            }

        /**
         * Convert the bit array to a 128-bit radix-10 (decimal) floating point number.
         */
        Dec128 toDec128()
            {
            return toVarDec().toDec128();
            }
        }


    // ----- ByteArray mixin -----------------------------------------------------------------------

    /**
     * Functionality specific to arrays of bytes.
     */
    static mixin ByteArray<Element extends Byte>
            into Array<Element>
        {
        /**
         * Obtain a view of this array as an array of bits. The array is immutable if this array is
         * immutable, and shares the mutability attribute of this array, except that the resulting
         * array is FixedSize if this array is Mutable.
         *
         * @return an array of bits that represents the contents of this array
         */
        // TODO Bit[] asBitArray()

        /**
         * Convert the byte array to an immutable array of bits.
         *
         * @return an immutable array of bits corresponding to the bytes in this array
         */
        immutable Bit[] toBitArray()
            {
            Int   bytecount = size;
            Int   bitcount  = bytecount * 8;
            Bit[] bits      = new Bit[bitcount];
            Int   index     = 0;
            EachByte: for (Byte byte : this)
                {
                for (Bit bit : byte.toBitArray())
                    {
                    bits[index++] = bit;
                    }
                }
            return bits.ensureImmutable(true);
            }

        /**
         * Convert the byte array to an immutable array of nibbles.
         *
         * @return an immutable array of nibbles corresponding to the bytes in this array
         */
        immutable Nibble[] toNibbleArray()
            {
//            Int      bytecount = size;
//            Int      nibcount  = bytecount * 2;
//            Nibble[] nibbles   = new Nibble[nibcount];
//            Int      index     = 0;
//            EachByte: for (Byte byte : this)
//                {
//                for (Nibble nibble : byte.toNibbleArray())
//                    {
//                    nibbles[index++] = nibble;
//                    }
//                }
//
//            return nibbles.ensureImmutable(true);
            TODO CP;
            }

        /**
         * Convert the byte array to a variable-length signed integer.
         *
         * @throws OutOfBounds  if the byte array is too large to be converted to a variable-length
         *                      signed integer
         */
        VarInt toVarInt()
            {
            assert:bounds size > 0;
            return new VarInt(this);
            }

        /**
         * Convert the byte array to a signed 8-bit integer.
         *
         * @throws OutOfBounds  if the byte array is too large to be converted to a 8-bit
         *                      signed integer, or if the resulting value is out of the signed
         *                      8-bit integer range
         */
        Int8 toInt8()
            {
            return new Int8(verifyMax2sComplementBytes(1));
            }

        /**
         * Convert the byte array to a signed 16-bit integer.
         *
         * @throws OutOfBounds  if the byte array is too large to be converted to a 16-bit
         *                      signed integer, or if the resulting value is out of the signed
         *                      16-bit integer range
         */
        Int16 toInt16()
            {
            return new Int16(verifyMax2sComplementBytes(2));
            }

        /**
         * Convert the byte array to a signed 32-bit integer.
         *
         * @throws OutOfBounds  if the byte array is too large to be converted to a 32-bit
         *                      signed integer, or if the resulting value is out of the signed
         *                      32-bit integer range
         */
        Int32 toInt32()
            {
            return new Int32(verifyMax2sComplementBytes(4));
            }

        /**
         * Convert the byte array to a signed 64-bit integer.
         *
         * @throws OutOfBounds  if the byte array is too large to be converted to a 64-bit
         *                      signed integer, or if the resulting value is out of the signed
         *                      64-bit integer range
         */
        Int64 toInt()
            {
            return new Int64(verifyMax2sComplementBytes(8));
            }

        /**
         * Convert the byte array to a signed 128-bit integer.
         *
         * @throws OutOfBounds  if the byte array is too large to be converted to a 128-bit
         *                      signed integer, or if the resulting value is out of the signed
         *                      128-bit integer range
         */
        Int128 toInt128()
            {
            return new Int128(verifyMax2sComplementBytes(16));
            }

        /**
         * Convert the byte array to a variable-length unsigned integer.
         *
         * @throws OutOfBounds  if the byte array is too large to be converted to a variable-length
         *                      unsigned integer
         */
        VarUInt toVarUInt()
            {
            assert:bounds size > 0;
            return new VarUInt(this);
            }

        /**
         * Convert the byte array to a unsigned 8-bit integer.
         *
         * @throws OutOfBounds  if the byte array is too large to be converted to a 8-bit
         *                      unsigned integer, or if the resulting value is out of the unsigned
         *                      8-bit integer range
         */
        UInt8 toByte()
            {
            return new UInt8(verifyMaxSignificantBytes(1));
            }

        /**
         * Convert the byte array to a unsigned 16-bit integer.
         *
         * @throws OutOfBounds  if the byte array is too large to be converted to a 16-bit
         *                      unsigned integer, or if the resulting value is out of the unsigned
         *                      16-bit integer range
         */
        UInt16 toUInt16()
            {
            return new UInt16(verifyMaxSignificantBytes(2));
            }

        /**
         * Convert the byte array to a unsigned 32-bit integer.
         *
         * @throws OutOfBounds  if the byte array is too large to be converted to a 32-bit
         *                      unsigned integer, or if the resulting value is out of the unsigned
         *                      32-bit integer range
         */
        UInt32 toUInt32()
            {
            return new UInt32(verifyMaxSignificantBytes(4));
            }

        /**
         * Convert the byte array to a unsigned 64-bit integer.
         *
         * @throws OutOfBounds  if the byte array is too large to be converted to a 64-bit
         *                      unsigned integer, or if the resulting value is out of the unsigned
         *                      64-bit integer range
         */
        UInt64 toUInt()
            {
            return new UInt64(verifyMaxSignificantBytes(8));
            }

        /**
         * Convert the byte array to a unsigned 128-bit integer.
         *
         * @throws OutOfBounds  if the byte array is too large to be converted to a 128-bit
         *                      unsigned integer, or if the resulting value is out of the unsigned
         *                      128-bit integer range
         */
        UInt128 toUInt128()
            {
            return new UInt128(verifyMaxSignificantBytes(16));
            }

        /**
         * Convert the byte array to a variable-length binary radix floating point number.
         *
         * @throws OutOfBounds  if the byte array is too large to be converted to a variable-length
         *                      radix-2 (binary) floating point number
         */
        VarFloat toVarFloat()
            {
            assert:bounds size >= 2 && size.bitCount == 1;
            return new VarFloat(this);
            }

        /**
         * Convert the byte array to a 16-bit radix-2 (binary) floating point number.
         *
         * @throws OutOfBounds  if the byte array is too large to be converted to a 16-bit radix-2
         *                      (binary) floating point number
         */
        Float16 toFloat16()
            {
            assert:bounds size == 2;
            return new Float16(this);
            }

        /**
         * Convert the byte array to a 16-bit radix-2 (binary) floating point number.
         *
         * @throws OutOfBounds  if the byte array is too large to be converted to a 16-bit radix-2
         *                      (binary) floating point number
         */
        BFloat16 toBFloat16()
            {
            assert:bounds size == 2;
            return new BFloat16(this);
            }

        /**
         * Convert the byte array to a 32-bit radix-2 (binary) floating point number.
         *
         * @throws OutOfBounds  if the byte array is too large to be converted to a 32-bit radix-2
         *                      (binary) floating point number
         */
        Float32 toFloat32()
            {
            assert:bounds size == 4;
            return new Float32(this);
            }

        /**
         * Convert the byte array to a 64-bit radix-2 (binary) floating point number.
         *
         * @throws OutOfBounds  if the byte array is too large to be converted to a 64-bit radix-2
         *                      (binary) floating point number
         */
        Float64 toFloat64()
            {
            assert:bounds size == 8;
            return new Float64(this);
            }

        /**
         * Convert the byte array to a 128-bit radix-2 (binary) floating point number.
         *
         * @throws OutOfBounds  if the byte array is too large to be converted to a 128-bit radix-2
         *                      (binary) floating point number
         */
        Float128 toFloat128()
            {
            assert:bounds size == 16;
            return new Float128(this);
            }

        /**
         * Convert the byte array to a variable-length decimal radix floating point number.
         *
         * @throws OutOfBounds  if the byte array is too large to be converted to a variable-length
         *                      radix-10 (decimal) floating point number
         */
        VarDec toVarDec()
            {
            assert:bounds size >= 4 && size.bitCount == 1;
            return new VarDec(this);
            }

        /**
         * Convert the byte array to a 32-bit radix-10 (decimal) floating point number.
         *
         * @throws OutOfBounds  if the byte array is too large to be converted to a 32-bit radix-10
         *                      (decimal) floating point number
         */
        Dec32 toDec32()
            {
            assert:bounds size == 4;
            return new Dec32(this);
            }

        /**
         * Convert the byte array to a 64-bit radix-10 (decimal) floating point number.
         *
         * @throws OutOfBounds  if the byte array is too large to be converted to a 64-bit radix-10
         *                      (decimal) floating point number
         */
        Dec64 toDec64()
            {
            assert:bounds size == 8;
            return new Dec64(this);
            }

        /**
         * Convert the byte array to a 128-bit radix-10 (decimal) floating point number.
         *
         * @throws OutOfBounds  if the byte array is too large to be converted to a 128-bit radix-10
         *                      (decimal) floating point number
         */
        Dec128 toDec128()
            {
            assert:bounds size == 16;
            return new Dec128(this);
            }

        /**
         * Internal helper to verify that a 2s-complement number will fit into the specified number
         * of bytes.
         *
         * @param n  the maximum number of bytes
         *
         * @return the Byte[] of the specified size
         *
         * @throws IllegalBounds  if this array cannot be used to form a 2s-complement integer of
         *                        the specified size
         */
        private Byte[] verifyMax2sComplementBytes(Int n)
            {
            assert:bounds size > 0;

            Int delta = n - size;
            switch (delta.sign)
                {
                case Zero:
                    // this byte array is the perfect size
                    return this;

                case Positive:
                    // this byte array is missing some leading bytes; fill them in with 0s or Fs
                    Byte fill = this[0] & 0x80 == 0 ? 0x00 : 0xFF;
                    return new Array<Byte>(n, i -> (i < delta ? fill : this[i-delta]));

                case Negative:
                    // make sure that all of the extra bytes are 0s or Fs (and then discard them)
                    Byte expect = this[delta] & 0x80 == 0 ? 0x00 : 0xFF;
                    for (Byte byte : this[0..-1-delta])
                        {
                        assert:bounds byte == expect;
                        }
                    return this[size-n .. size);
                }
            }

        /**
         * Internal helper to verify that a 2s-complement number will fit into the specified number
         * of bytes.
         *
         * @param n  the maximum number of bytes
         *
         * @return the Byte[] of the specified size
         *
         * @throws IllegalBounds  if this array cannot be used to form a 2s-complement integer of
         *                        the specified size
         */
        private Byte[] verifyMaxSignificantBytes(Int n)
            {
            assert:bounds size > 0;

            Int delta = n - size;
            switch (delta.sign)
                {
                case Zero:
                    // this byte array is the perfect size
                    return this;

                case Positive:
                    // this byte array is missing some leading bytes; fill them in with 0s
                    return new Array<Byte>(n, i -> (i < delta ? 0 : this[i-delta]));

                case Negative:
                    // make sure that all of the extra bytes are zeros (and then discard them)
                    for (Byte byte : this[0..-1-delta])
                        {
                        assert:bounds byte == 0;
                        }
                    return this[size-n .. size);
                }
            }

        /**
         * Read a packed integer value from within the byte array at the specified offset, and
         * return the integer value and the offset immediately following the integer value.
         *
         * @param offset  the offset of the packed integer value
         *
         * @return the integer value
         * @return the offset immediately following the packed integer value
         */
        (Int value, Int newOffset) readPackedInt(Int offset)
            {
            // use a signed byte to get auto sign-extension when converting to an int
            Int8 b = this[offset].toInt8();

            // Tiny format: the first bit of the first byte is used to indicate a single byte format,
            // in which the entire value is contained in the 7 MSBs
            if (b & 0x01 != 0)
                {
                return (b >> 1).toInt(), offset + 1;
                }

            // Small and Medium formats are indicated by the second bit (and differentiated by the
            // third bit). Small format: bits 3..7 of the first byte are bits 8..12 of the result,
            // and the next byte provides bits 0..7 of the result. Medium format: bits 3..7 of the
            // first byte are bits 16..20 of the result, and the next byte provides bits 8..15 of
            // the result, and the next byte provides bits 0..7 of the result
            if (b & 0x02 != 0)
                {
                Int n = (b >> 3).toInt() << 8 | this[offset+1].toInt();

                // the third bit is used to indicate Medium format (a second trailing byte)
                return b & 0x04 != 0
                        ? (n << 8 | this[offset+2].toInt(), offset + 3)
                        : (n, offset + 2);
                }

            // Large format: the first two bits of the first byte are 0, so bits 2..7 of the
            // first byte are the trailing number of bytes minus 1
            Int size = 1 + (b >>> 2).toInt();
            assert:bounds size <= 8;

            Int curOffset  = offset + 1;
            Int nextOffset = curOffset + size;
            Int n          = this[curOffset++].toUnchecked().toInt8().toInt();  // sign-extend
            while (curOffset < nextOffset)
                {
                n = n << 8 | this[curOffset++].toInt();
                }
            return n, nextOffset;
            }
        }


    // ----- Orderable mixin -----------------------------------------------------------------------

    static mixin Orderer<Element extends Orderable>
            into Array<Element>
            extends Comparator<Element>
            implements Orderable
        {
        /**
         * Compare two arrays of the same type for purposes of ordering.
         */
        static <CompileType extends Orderer>
                Ordered compare(CompileType array1, CompileType array2)
            {
            for (Int i = 0, Int c = Int.minOf(array1.size, array2.size); i < c; i++)
                {
                Ordered order = array1[i] <=> array2[i];
                if (order != Equal)
                    {
                    return order;
                    }
                }

            return array1.size <=> array2.size;
            }
        }


    // ----- Hashable mixin ------------------------------------------------------------------------

    static mixin Hasher<Element extends Hashable>
            into Array<Element>
            extends Comparator<Element>
            implements Hashable
        {
        /**
         * Calculate a hash code for a given array.
         */
        static <CompileType extends Hasher> Int hashCode(CompileType array)
            {
            Int hash = 0;
            for (CompileType.Element el : array)
                {
                hash += CompileType.Element.hashCode(el);
                }
            return hash;
            }
        }

    // ----- the base mixin for Orderable and Hashable ---------------------------------------------

    static mixin Comparator<Element>
            into Array<Element>
        {
        /**
         * Compare two arrays of the same type for equality.
         *
         * @return true iff the arrays are equivalent
         */
        static <CompileType extends Comparator>
                Boolean equals(CompileType array1, CompileType array2)
            {
            if (array1.size != array2.size)
                {
                return False;
                }

            for (Int i = 0, Int c = Int.minOf(array1.size, array2.size); i < c; i++)
                {
                if (array1[i] != array2[i])
                    {
                    return False;
                    }
                }

            return True;
            }
        }
    }
