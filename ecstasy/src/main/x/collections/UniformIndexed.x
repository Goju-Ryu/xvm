/**
 * UniformIndexed is an interface that allows the square bracket operators to be used with a
 * container data type that contains elements of a specified type, indexed by a specified type.
 * This is commonly known as "array access", although Ecstasy allows access in this manner, via
 * this interface, to data structures that are not arrays.
 */
interface UniformIndexed<Index, Element>
    {
    /**
     * Obtain the value of the specified element.
     */
    @Op("[]") Element getElement(Index index);

    /**
     * Modify the value in the specified element.
     */
    @Op("[]=") void setElement(Index index, Element value)
        {
        throw new ReadOnly();
        }

    /**
     * Obtain a Ref for the specified element.
     */
    Var<Element> elementAt(Index index)
        {
        return new SimpleVar<Index, Element>(this, index);

        /**
         * An implementation of Var that delegates all of the complicated Ref responsibilities to
         * the return value from the {@link UniformIndexed.get} method.
         */
        class SimpleVar<Index, Element>(UniformIndexed<Index, Element> indexed, Index index)
                delegates Var<Element>(ref)
            {
            @Override
            Boolean assigned.get()
                {
                return True;
                }

            @Override
            Element get()
                {
                return indexed.getElement(index);
                }

            @Override
            void set(Element value)
                {
                indexed.setElement(index, value);
                }

            private Var<Element> ref.get()
                {
                Element value = get();
                return &value;
                }
            }
        }
    }
