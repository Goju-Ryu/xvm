// TODOC - outermost item in a file needs to have explicit access modifier (context can not be assumed to be implicit beyond file boundary)
public class Example
    {
    protected int?[] x = new int[5];
    private People[] = new People[5] -> f();
    }

const Point(Int x, Int y);

class Point implements Const // and is immutable after construction
    {
    construct(Int x, Int y)
        {
        this.x = x;
        this.y = y;

        meta.immutable = true;
        }

    Int x;
    Int y;

    // hashcode
    // equals
    // tostring
    // etc.
    }

Point origin = new Point(y:0, x:0);

trait AddHashcode
    {
    int hashcode()
        {
        import x.List as Putty;

        return super() + new Putty("hello", "world").hashcode();
        }
    }

interface Bar extends Foo
    {
    }

interface Foo<T>
    {
    Foo<T1> op<T1, T2>(Foo<T2> f);
    }

public class TraitExample
    {
    Object foo(@AddHascode Object o)
        {
        return o;
        }

    void test()
        {
        RA ra = new RA();
        ra.
        }

    private class RA // implements Runnable
        {
        private void run()
            {
            // TODO...
            }

        Runnable as<Runnable>()
            {
            return this:private; // this would be bad
            }

        void runAsync()
            {
            // TODO...
            }
        }

    foo(Ref<Integer> i)
        {
        int x = i++;   // i is an l-value
        // t = i.incAndGet(1);
        // x.set(t);
        }

    int v = 1;
    foo(v!);
    S.O.print(v); // 2
    }


value String(@ro char[] Chars)
    {
    foo
        {
        String | Runnable s = foo();
        if (s  insof Strring)
            {
            s.length;
            }
        }

    @lazy int Hashcode
        {
        int get() {...}
        }
    }



-- this --

@future int i = svc.next();
if (!i!.isDone(100ms)) {...}

-- or that --

Future f = i!;
if (!f.isDone(100ms)) {...}

--

if (!&i.isDone(100ms)) {...}   // 1 vote - it's the same FUGLY as C++
if (!i&.isDone(100ms)) {...}   // 1 vote - it's the same FUGLY as C++
if (!(i).isDone(100ms)) {...}
if (!<i>.isDone(100ms)) {...}
if (!^i.isDone(100ms)) {...}

public value Point(int x, int y);
Point p = ...
Property prop = p.&x;
Method mGet = p.&x.&get;


public class Whatever { void foo(); int foo(int x); void foo(String s); }
Method m = Whatever.foo(int)!;

Whatever w = ...
Function f = w.&foo();
Function f = w.foo; // ??? overloaded

&x.y    x!.y
(&x).y  x!.y
&(x.y)  x.y!
x.&y    x.y!


class Person {
  int age;
  boolean oldEnough();

  void foo(Map<String, Person> map) {
    map.values(oldEnough);
    map.values(p -> p.age > 17);
  }
  static int add(int a, int b) -> a + b;
  }


class Map<K,V>
  {
  Set<V> values(boolean filter(V v))
    {
    entries(e -> filter(e.value)).map(e -> e.value);
    }
  }

class Filter1<V>
  {
  boolean evaluate(V v);
  }

class Map<K,V>
  {
  function<V> boolean Filter2(V v);

  Set<V> values(Filter2 filter)

  Set<V> values(Any filter)
    {
    entries(e -> filter(e.value)).map(e -> e.value);
    }
  }

test() {
  Filter2<Person> f2 = p -> p.isMale;
  foo(map.values(f2));

  Filter1<Person> f1 = getFilter1FromSomewhere();
  foo(map.values(f1.evaluate);

  foo(map.values(p -> p.isMale);

  Bob bob = new Bob();
  foo(map.values(bob.testBob(?, 3));
  foo(map.values(Bob.testAge(7));  // REVIEW
}

class Bob extends Person {
  boolean testBob(Person o, int x) {..}
  boolean testAge(int x) {..}
}

foo(Map<String, Person> map) {
  class Any {
    boolean Bar(Person p);
  }
  function boolean Bar(Person p);
  static Bar X = p -> p.age > 17;
  map.values(X);
}

--

module M1 {
  package P1 {
    class C1 {
    #ifdef V1
      int X;
      public void foo() {..};
   #else ifdef V2
      long X;
      private void foo() {..};
      #endif
    }
  }
}

import M1;
module M2 {
  class C2 {
    void main() {
      C1 o = new M1.P1.C1();
      print o.X;
      print o.Y;    // compiler error! no such property
      #ifdef DEBUG
      print o.Y;
      #endif
    }
  }
}


module M3 {
  package P3 {
    class C3 {
      #ifdef TEST
        int X;
      #else
        long X;
      #endif
    }
  }
}


#ifdef A
  ...
  #ifdef B
  ...
  #endif
#endif

#ifdef B
 ..
 #ifdef A
 ..
 #endif
#endif

// for some <T>

boolean f(T& value)

T value;
while (f(&value))
  {
  ...
  }

//

interface Listener<E>
  {
  void notify(E event);  // this method implies "consumer" aka "? super .."
  E getLastEvent();      // this method implies "provider" aka "? extends .."
  }

interface NamedCache<K, V>
  {
  void addValueListener(Listener<? super V> listener)
    {
    m_listener = listener;
    }

  void repeat()
    {
    // legal:
    m_listener.notify(m_listener.getLastEvent());

    // illegal:
    Object o = m_listener.getLastEvent();
    m_listener.notify(o);

    // legal:
    Object o = m_listener.getLastEvent();
    m_listener.notify((V) o);
    }

  void put(K key, V value)
    {
    m_listener.notify(value);
    }
  }

class SomeApp
  {
  @inject Listener listenerOfAnything;
  @inject NamedCache<String, Person> people;

  void main()
    {
    people.addValueListener(listenerOfAnything); // compiler error???
    }
  }

// example 2 with auto-infer of consumer/producer "? super" crap

interface Listener<E>
  {
  void notify(E event);  // this method implies "consumer" aka "? super .."
  }

interface NamedCache<K, V>
  {
  void addValueListener(Listener<V> listener)  // implied: "? super"
    {
    m_listener = listener;
    }

  void test(Object o, V value)
    {
    // legal:
    m_listener.notify(value);

    // illegal:
    m_listener.notify(o);
    }

  void put(K key, V value)
    {
    m_listener.notify(value);
    }
  }

class SomeApp
  {
  @inject Listener listenerOfAnything;
  @inject Listener<Person> listenerOfPerson;
  @inject Listener<String> listenerOfString;
  @inject NamedCache<String, Person> people;

  void main()
    {
    // legal (!!!)
    people.addValueListener(listenerOfAnything);

    // legal
    people.addValueListener(listenerOfPerson);

    // illegal (!!!)
    Listener listener2 = listenerOfString;
    }
  }

// example 3 with auto-infer of both consumer/producer

interface Listener<E>
  {
  void notify(E event);
  E getLastEvent();
  }

interface NamedCache<K, V>
  {
  void addValueListener(Listener<V> listener)
    {
    m_listener = listener;
    }

  void test(Object o, V value)
    {
    // legal:
    m_listener.notify(value);

    // illegal:
    m_listener.notify(o);

    // legal:
    Object o2 = listener.getLastEvent();

    // legal:
    V v2 = listener.getLastEvent();
    }

  void put(K key, V value)
    {
    m_listener.notify(value);
    }
  }

class SomeApp
  {
  @inject Listener listenerOfAnything;
  @inject Listener<Person> listenerOfPerson;
  @inject Listener<String> listenerOfString;
  @inject NamedCache<String, Person> people;

  void main()
    {
    // illegal (!!!)
    people.addValueListener(listenerOfAnything);

    // legal
    people.addValueListener(listenerOfPerson);

    // illegal?
    people.addValueListener(listenerOfString);
    }
  }

// example 4

// the goal with container types and generics is to be able to have a "List of String"
// be usable as a "List of Object". In other words, just like a List can be treated as
// an Object, and a String can be treated as an Object, a List<String> should be treatable
// as a List<Object>

// the first challenge of this is that when one treats a List<String> as a List<Object>,
// that implies that methods that would otherwise only accept a String must now also
// accept any Object. more specifically, it's not that the List has to accept any object,
// but that it is possible for the List to expose itself as a more generic container, i.e.
// as a container of any Object
List<String> listString = new List<String>();
// so what can I add to a List of String? just a String; nothing else
listString.add("hello"); // legal
listString.add(new Object()); // illegal - compile time error! (and it would also be a RTE)
// but a List<String> is (i.e. can be used as) a List<Object> right?
List<Object> listObject = listString; // legal
// so what can I add to a List of Object? any Object, right?!?!
listObject.add("hello"); // legal
listObject.add(new Object()); // compiler does not detect the error, but it _IS_ a RTE!

// in this manner, container types (parameterized types) act like "array" does in Java.
// consider the method:
public void foo(List<Object> list) {...}
// now, using the list from the above example:
foo(listString); // legal
foo(listObject); // legal

// so let's imagine what occurs within foo(), and how that actually works:
public void foo(List<Object> list)
    {
    // take the first item out of the list
    Object o = list.remove(0);
    // add that item to the end of the list
    list.add(o);
    }

// ok, that should work fine. if something is in the list, it shouldn't be
// a problem sticking it back in, right? but what if it did something like this?
public void foo(List<Object> list)
    {
    // add a plain old object to the end of the list
    list.add(new Object());
    }
// this one should fail at runtime! because in reality the list is a list of string,
// and the "new Object()" is not a string! but what it implies is that the ref being
// passed to foo() is actually a ref to a List<Object>, in that there exists a method
// for List.add(Object) for example. and somehow that List.add(Object) goes to a piece
// of code that verifies (type asserts) that the argument is actually a String

// let's consider this in terms of "auto cast from sub-class towards object" versus
// "auto cast from object (or some other super class) to sub-class". when a List<String>
// is passed to a method that takes List<Object>, that is an implicit "auto-cast from
// String to Object" on the "out values" (e.g. return values), and an implicit "auto-cast
// from Object to String" on the "in values" (e.g. parameter arguments).

// to illustrate this, let's create two interfaces, one that is for return values and one
// that is for parameters:
interface Extractor<T>
    {
    public T first();
    }
interface Logger<T>
    {
    public void add(T value);
    }

// let's also assume that the List interface includes the same:
interface List<T>
    {
    public T get(Int i);
    // ...
    public T first();
    public T last();
    // ...
    public void add(T value);
    public boolean remove(T value);
    // etc. ...
    }

// so anything that implements List<T> also implements Extractor<T> and Logger<T>.
// let's imagine methods that take these interfaces:
void doObjectLogging(Logger<Object> logger)
    {
    logger.add(new Object());
    }
void doStringLogging(Logger<String> logger)
    {
    logger.add("hello");
    }
void doObjectExtracting(Extractor<Object> extractor)
    {
    Object o = extractor.first();
    }
void doStringExtracting(Extractor<String> extractor
    {
    String s = extractor.first();
    }

// now, going back to our previous example:
doObjectLogging(listString);    // compile: OK. runtime: RTE!
doStringLogging(listString);    // compile: OK. runtime: OK.
doObjectExtracting(listString); // compile: OK. runtime: OK.
doStringExtracting(listString); // compile: OK. runtime: OK.

doObjectLogging(listObject);    // compile: OK. runtime: RTE!
doStringLogging(listObject);    // compile: ERR! (cast required). runtime: OK.
doStringLogging((List<String>) listObject); // compile: OK. runtime: OK.
doObjectExtracting(listObject); // compile: OK. runtime: OK.
doStringExtracting(listObject); // compile: ERR! (cast required). runtime: OK.
doStringExtracting((List<String>) listObject); // compile: OK. runtime: OK.

// Now, let's introduce a List that only contains objects:
List<Object> listOnlyObject = new List<Object>();
// and let's fix the obvious compile errors and see the results:
doObjectLogging(listOnlyObject);                    // compile: OK. runtime: OK.
doStringLogging((Logger<String>) listOnlyObject);     // compile: OK. runtime: RTE. (not List<String>)
doObjectExtracting(listOnlyObject);                 // compile: OK. runtime: OK.
doStringExtracting((Extractor<String>) listOnlyObject);  // compile: OK. runtime: RTE. (not List<String>)

//                   widening                narrowing
// T<P1> -> T<P2>    P1=String -> P2=Object  P1=Object -> P2=String
// ----------------  ----------------------  -------------------------
// !(T consumes P1)  1. Implicit conversion  2. Illegal (Compile Time Error)
// !(T produces P1)  3. Implicit conversion  4. Implicit conversion
//                      (but possible RTE)

// so, for a genericized type parameterized with T, we say that the type "consumes T" iff there
// is at least one method that satisfies any of the following:
// (1) has T as a parameter type;
// (2) has a return type that "consumes T";
// (3) has a parameter type that "produces T".

// for a genericized type parameterized with T, we say that the type "produces T" iff there
// is at least one method that satisfies any of the following:
// (1) has T as a return type;
// (2) has a return type that "produces T";
// (3) has a parameter type that "consumes T".

// while we define "produces" and "consumes" in the positive sense, we only use it in the
// negative sense

// implicit converson from T2 to T1 example:
// T2 v2 = ...
// T1 v1 = v2;
//
// implicit conversion from non-genericized type T2 to T1;
// 1) if T2 === T1
// 2) if T2 > T1 (e.g. T2 is a sub-class of T1, e.g. T2 is String and T1 is Object)
//
// implicit converson from T2<P2> to T1<P1> example:
// T2<P2> v2 = ...
// T1<P1> v1 = v2;
//
// implicit conversion from genericized type T2<P2> to T1<P1>:
// 1) if the non-genericized T2 can be implicitly converted to the non-genericized T1, ___AND___
// 2) a) if the non-genericized P2 can be implicitly converted to the non-genericized P1 __OR__
//    b) if P1 > P2 (e.g. P1 is String, P2 is Object) _AND_ T1 does not "produce" P1

// --

// java helper
List<T> <T extends Comparable> sort(List<T> list) {...}

// xtc equivalent
List<list.T> sort(List<Comparable> list) {...}

--

interface List<T>
  {
  void add(T value);
  int size;
  T get(int i);
  }

class ArrayList<T>
    implements List<T>
  {
  // ...
  }

interface Map<K,V>
  {
  interface Entry<K,V> // auto-picked-up from Map? or do they need to be spec'd?
    {
    K key;
    V value;
    }
  // ...
  }

void foo(Map<K,V> map)
  {
  foo2(new ArrayList<map.Entry>);
  }

void foo2(List<Map.Entry> list)
  {
  list.T.K key = list.get(0).key;
  list.T.V val = list.get(0).value;
  }

--

// return types

void foo();

String foo();

(String, Int) foo();

// with names
(String name, Int age) foo();

// method type params

T foo<T>();

(T1, T2) foo<T1, T2, T3>(T3 value);

// method params

--

// tuple example
Tuple t = (1, "hello");
Int n = t[0];
String s = t[1];
Int n2 = t[1]; // compile time error (and obviously it would be a runtime error if it could compile)

// basically, it's some way to do non-uniform index of an "array" like structure
// i.e. fields by index
interface UniformIndexed<IndexType, ValueType>
interface Indexed<ValueType...>

--

class MyFunction<P1, P2, R>
    {
    Tuple<P1, P2> Params;

    R foo(P1 p1, P2 p2) {...}
    }


Class clz = MyFunction;

...
MyFunction f = new MyFunction();

--

    interface Tuple<FieldTypes...>

void foo(Tuple<Int, String> t)
  {
  Int i = t[0];
  }

void foo(Tuple<T0, T1> t)
  {
  T0 val0 = t[0];
  T1 val1 = t[1];
  }

// so for a multi-type list ("..." format), each element can be referred to using:
// 1) a constant
// 2) a parameter

interface Tuple<FieldTypes...>
    {
    FieldTypes[index] get(Int index);
    void set(Int index, FieldTypes[index] newValue);
    // ...
    }

void foo(Tuple t)
    {
    t.FieldTypes[0] val0 = t[0]; // ugly but correct
    }

void foo(Tuple t, Int i)
    {
    t.FieldTypes[i] val = t[i]; // even uglier but correct
    }

// --

// numbers:
// how to have number literals be usable as "int" or "float"?
// how to have support for various sized and signed ints? signed/unsigned, 8/16/32/64/128/etc.
// how to have detection for out of bounds numbers? underflow/overflow

Number PI = 3.14159267;
Float f = PI;
Dec d = PI;
Integer MAX_UINT8 = 255;
Int n = MAX_UINT8;

Byte b = 0x00;  // compile time OK, runtime OK
Byte b = 0xFF;  // compile time OK, runtime OK
Byte b = -1;    // compiler error, RTE
Byte b = 0x100; // compiler error, RTE
// so the compiler has to know something here about the range of Byte
// or the compiler has to know how to use Byte at compile time to find out what is legal here
// the values (0x00, 0xFF, -1, 0x100) are all "literals"
// what if instead it were a variable?
Int n = 255;
Byte b = n; // should this be a compiler error? it wouldn't be an RTE!
// or this instead
Byte b = n.to<Byte>();
// so what does this mean?
Byte b = (Byte) n; // error --> n is a ref to Int, not Byte! this is attempting to assign the ref itself
// maybe there is an @auto that one can add to a "to<T>()" method to indicate that the compiler add it automatically
class Int {... @auto Byte to<Byte>() {...}}
// then there could be an IntegerLiteral type with:
@auto Int16 to<Int16>()
@auto Int32 to<Int32>()
@auto Int64 to<Int64>()
@auto Int128 to<Int128>()
@auto Dec32 to<Dec32>()
@auto Dec64 to<Dec64>()
@auto Dec128 to<Dec128>()
@auto Float32 to<Float32>()
@auto Float64 to<Float64>()
@auto Float128 to<Float128>()
// etc.
// and a FPNumberLiteral type with:
@auto Dec32 to<Dec32>()
@auto Dec64 to<Dec64>()
@auto Dec128 to<Dec128>()
@auto Float32 to<Float32>()
@auto Float64 to<Float64>()
@auto Float128 to<Float128>()
// so the compiler simply needs to map literal numeric values to one of two
// "constant types", that in turn map to IntegerLiteral or FPNumberLiteral

// --
Int64  x = ..
Int32  y = ..
UInt16 z = ..
Int64 r = x * y * z; // or some similar
// how are these "combined" / converted?
// 1) It's pretty obvious that Int64 should have "Int64 mul(Int64 n);"
// 2) Does it _also_ have a mul(Int32) and mul(UInt16) and so on?
// 3) Or does it have a mul(Integer)?
// 4) And/or a mul(Number)?

//--

// java int-to-byte assignment
int n = ...
byte b = (byte) n; // NEVER EVER EVER EVER EVER CORRECT! NOT ONE SINGLE TIME!!!
byte b = (byte) (n & 0xFF); // ALWAYS! EVERY SINGLE TIME! EVERY SINGLE TIME CORRECT!

byte b = 128; // ILLEGAL!!! COMIPLER ERROR!!!

// -- min/max
int x;
int y;
z = x.max(y);


// ---

Array<ElementType> : List<..>
  {
  int length;
  ElementType get(int)
  void set(int, ElementType)
  Element<ElementType> elementAt(int)

  Element<ElementType> : Ref<..>
    {
    value = get()
    set(value)
    }
  }

List<String> ls = ..
// can't do this
// List<? extends Object> lo = ls;
List<Object> lo = ls; // is this a warning? (???) is it legal? (yes)

foo1(ls);
foo2(ls);

foo1(ls, new Object());

void foo1<T>(List<T> ao, T v) {
ao.add(v);
}

void foo2(List<Object> ao) {
  ao.set(0, new Object()); // calls the method "set(int,Object)V" on the ao reference
}
foo(ls); // does NOT have a "set(int,Object)V" ... does it? needs a "shim"?

// tools:
// (1) shim
// (2) immutable
// (3) @ro

// so how about this:
foo(@ro Object[] ao)
  {
  ao.set(0, new Object()); // fails -- at COMPILE time
  }

Set<V>
  {
  boolean contains(V value) {...}
  boolean contains(!V value) {return false;}
  // or ...
  boolean contains(V value) {...} else {return false;}
  // this would have been the shim provided by the runtime if we didn't have a "!V":
  // boolean contains(Object value) {if value instanceof V return contains((V) value) else throw RTE}


  boolean add(V value) {...}
  // shim:
  boolean add(!V value) {throw RTE}
  // or to make mark happy
  boolean add(SHIMTYPE value) {if value instanceof V return add((V) value) else throw RTE}
  }

// -- how to do immutability?

Object
  protected Meta meta

Meta
  boolean Immutable
  // TODO composition of the type itself / reflection

// -- function / method / type short-hand

typedef Map<Stirn, Person> People;

Iterator<ElementType> iter =
    {
    conditional ElementType next()
        {
        if (...)
            {
            return true, list[i];
            }

        return false;
        }
    ElType el;
    if (el : iter.next())
        {
        print(el);
        }
    }

// equality

// when i say:
if (a == b) {...}
// how does that compile?
// first of all, compile time type of a and b must be equal
// second, the compile time type must _contain_ function Boolean equals(CTT value1, CTT value2)

Int i1 = 1;
Int i2 = 2;
Number n1 = i1;
Number n2 = i2;
if (i1 == i2) {...}
if (n1 == n2) {...}
if (i1 == n2) // CTE!!!

if (&i1 == &i2) {...} // false
if (&i1 == &n1) {...} // TRUE !!!!!!
i2 = 1;
if (&i1 == &i2) {...} // TRUE !!!!!!

Object o1, o2;
// ...
if (o1 == o2) // CTE!!!
if (&o1 == &o2)
if (&p1 == &o2)
if (((Object) p1) == o2)

// --- function as dynamic proxy

// let's say we have a function
function (Int, Boolean) fn(Int, Int) = (x, y) -> {x+y, True};

(i, b) = fn(1,2);

function Int (String) fn = String.length;
String s = "hello";

function String (Int) fn = s.substring(2, _);

function Int () fn = s.length.get;

// we know that it takes some number of parameters (0+) and has some number of return values (0+)
Int x  = fn(1, 2);
Int x2 = fn.invoke((1,2));
Int x3 = fn.invoke.invoke(((1,2)));
Int x4 = fn.invoke.invoke.invoke((((1,2))));

Function fn3 = fn.invoke;
fn3.invoke(((1,2)));

Function fn2 = fn;
((function Int fn(Int, Int)) fn2)(3, 4); // duh
Tuple tupleReturnValues = fn2.invoke(tupleParams);

fn = fn2; // this is the only question
// i.e. ...
function Int fn3(Int, Int) = fn2.to<function Int (Int, Int)>;

// so now I just have to create a Function
Function<f.ReturnTypes, f.ParamTypes> once(Function f)
    {
    return new Function()
        {
        Tuple invoke(Tuple params)
            {
            if (!alreadyDone)
                {
                prevReturnValue = f.invoke(params);
                alreadyDone = true;
                }
            return prevReturnValue;
            }
        Boolean alreadyDone = false;
        Tuple prevReturnValue = Void;
        }
    }

function Int (Int, Int) fn4 = once(fn).to<function Int (Int, Int)>;

Int test1 = fn4(1,2);
Int test2 = fn4.invoke((1,2))

// ---

function Void (Int) consumer = ...
function Void consumer(Int) {...}


foo(function Void consumer1(Int), function Void consumer2(Int))

// ---

service A
    {
    Void main()
        {
        B b = new B(this.bar);

        try critical
            {
            @future Boolean f1 = b.foo();

            // do some local mutation
            list.foreach(...);
            // etc. ...

            @future Boolean f2 = b.foo();

            // do some local mutation
            list.add(...);
            // etc. ...
            }
        catch (Deadlock e)
            {
            // WTF can we do anyhow?
            }

        Boolean f3 = f2.get();
        }

    Void bar()
        {
        // do some local mutation
        // ...
        list.add(...);
        // ...
        list.remove(...);
        // ...
        list.foreach(...);
        // ...
        }

    private List list = new List();
    }

service B(function Void fn())
    {
    Boolean foo()
        {
        Boolean f1 = false;

        // call out
        fn();

        // do something
        // ...
        f1 = ...
        // ...

        // call out
        fn();

        // do something else
        // ...
        f1 = ...
        // ...

        // call out
        fn();

        return f1;
        }
    }

// ---

assert expr;            // conditional assert: assertions must be enabled

assert:test expr;       // conditional assert: assertions ("test" level) must be enabled

assert:debug expr;      // conditional assert: assertions ("debug" level) must be enabled

assert:always expr;     // unconditional assertion (assertions do not have to be enabled)

assert:once expr;       // unconditional assertion that only happens one time

if:test
if:debug
if:present


assert:once ->
    {
    Boolean f = ...
    Int x = ...
    for (Int i = ...)
        {
        return false;
        }
    return true;
    };

if (@assertionsenabled)
    {
    static Boolean ALREADYDONE = false;
    if (!ALREADYDONE)
        {
        ALREADYDONE = true;
        Boolean f = ...
        Int x = ...
        for (Int i = ...)
            {
            assert ...;
            }
        }
    }

assert:once foo();

if (@assertionsenabled)
    {
    static Boolean ALREADYDONE = false;
    if (!ALREADYDONE)
        {
        ALREADYDONE = true;
        assert foo();
        }
    }

//

enum Key<ValType> {
    HOST<String>,
    PORT<Int>,
    SCORE<Dec>
}

interface PropertiesStore {
    Void put(Key key, Key.ValType value);
    Key.ValType get(Key key);
}


//

class B
    {
    Object foo1() {...}
    String foo1() {...}

    B! foo2() {...}

    Long foo3() {...}
    String foo3() {...}
    Object foo3() {...}

    Object foo4() {...}
    }

class D
        extends B
    {
    String foo1<Object>() {...}     // hides B.foo1()

    B foo2() {...}          // error
    D foo2() {...}          // hides B.foo2()

    Object foo3() {...}     // hides B.foo3()
    String foo3() {...}     // another foo3()

    String foo4() {...}     // hides B.foo4()
    }

//

interface B
    {
    Int foo()
        {
        return super();    //error
        }
    }

interface D extends B
    {
    Int foo()
        {
        return super();     // ok
        }
    }

//

interface B
    {
    Int foo()
        {
        return ...;
        }
    Int bar()
        {
        return ...;
        }
    }

interface D
    {
    Int foo()
        {
        return ...;
        }
    Int bar()
        {
        return ...;
        }
    }

interface C
        extends B
        extends D
    {
    Int foo()
        {
        return super();     // B.foo()
        }
    Int bar()
        {
        return super();     // B.bar()
        }
    }

//

// interruptable when:
// 1) you pop your service stack by returning from the zero-eth stack frame
// 2) you call yield, assuming there is such a thing

critical
    {
    // ...
    }

// or ...
try:critical
    {
    // ...
    }
catch (Deadlock e)
    {
    // ...
    }

// or ...
try (new CriticalSection())
    {
    // ...
    }
catch (Deadlock e)
    {
    // ...
    }

// a service is (obviously) an instance of Service, and Service has a “Boolean reentrant;”
// property; setting to false disallows reentrancy (causes an exception if reentrance is
// attempted)

// lastly, based on the obvious truth that “a call to console.log() should NEVER EVER CAUSE
// RE-ENTRANCY” etc., a service will be declarable in a way to say “i don’t care whether
// you’re in a critical section or not; i promise to never do anything that will call you
// back”, which is to say “from here on down is a terminal”
@nevergonnagiveyouup @nevergonnaletyoudown @nevergonnarunaroundanddesertyou service Logger
    {
    // ...
    }

//

service Toaster
    {
    Void toast(Bread bread) {...}
    }

class C
    {
    Void main()
        {
        Toaster+Service toaster = new Toaster();

        assert toaster instanceof Toaster;
        assert toaster instanceof Service;
        assert !(&toaster.as<Toaster>() instanceof Service);

        guardian.guard(&toaster.as<Service>());
        chef.doYourThing(&toaster.as<Toaster>());

        Service<Toaster> toasterService = somefactory.create(); // has
        Toaster toaster = toasterService.getImpl();
        }
    }

interface Service
    {
    @ro Boolean busy;
    @ro Int backlog;
    Void pleaseStop();
    Void iWasn'tKiddingPleasePleaseStop();
    Void fuckYouIsaidStopPleaseDoItNowOrI'llKillYou();
    Void kill();
    }

// --

const Point(Int x, Int y);

Point p = new Point(0, 0);
Int x = p.x;
p.x = 5;            // ok it's an error but only because it's a const
Property<Int> prop = &(p.x);
prop.set(5);        // ok it's an error but only because it's a const
Int x2 = prop.get();
PropertyInfo pi = Point.x;
Method mp = Point.somemethodthatdoesreflectionandgivesmeamethodbyname("x");

Int x3 = p.*pi.get();

function Int fn() = &(prop.get)

//

(Int result, Int remainder) = 5 /% 3;

//

try
    {
    // x ...
    }
catch (RTE e)
    {
    // y ...
    }
finally
    {
    // z ...
    }

GUARDALL
GUARD 1 RTE label1
    // x ...
ENDGUARD
JMP label2
label1:
    // y ...
FINALLY
    // z ...
ENDFINALLY
label2:

//

interface Doable
    {
    List do();
    }

const SuperDuper (List & AutoImmutable list)
        implements Doable
    {
    Boolean filter(String s)
        {
        return list.contains(s);
        }
    }

List   l = ["a", "b"];
Doable d = new SuperDuper(l);
function Boolean(String) f = d.filter;
function Boolean(String) f = s -> l.contains(s);

function void f() = new SuperDuper().do;

//

Int? x = ...
// what methods exist on the interface for "x"?
// - the intersection of the methods from Int and the methods from Nullable

// constructors

const Person(String name, Date dob)
    {
    construct Person(String name, Date dob)
        {
        // explicit validation
        assert:always name != "hitler";

        // this would be stupid to do, but isn't fatal
        this.name = name;

        this.firstname = ...;  // compiler error!

        // implicit call to:
        // this.name = name;
        // this.dob = dob;
        }

    String firstName
        {
        String get()
            {
            // ... split the name
            String s = super();
            if (s.startsWith
            return ...;
            }
        }

    String lastName
        {
        String get()
            {
            // ... split the name
            return ...;
            }
        }
    }

trait Taxable(String taxid);

const Employee(String name, Date dob, String taxid)
        extends Person(name, dob)
        incorporates Taxable(taxid)
    {
    construct Employee(String name, Date dob, String taxid)
        {
        // validation code here
        assert:always name.length > 0 -> "Name is too short %s".format(name);

        // construct Person(name, dob);
        // construct Taxable(taxid);
        }

    @lazy(this.calcRate) Dec taxrate;
    private Dec calcRate() {...}

    // alt
    @lazy Dec taxrate
        {
        Dec evaluate() {...}
        }

    }


// mutable capture of lvar ref

// stupid example
Int foo(function Void(Ref<Int>) do)
    {
    Int x = 3;

    do(&x);

    return x;
    }

// problem example
Ref<Int> foo()
    {
    Int x = 3;

    Ref<Int> xref = &x;

    // what is the value? 3, right?
    print xref.get();
    x = 5;
    // what is the value? 5, right?
    print xref.get();

    // off-topic example
    Ref<Ref<Int> xrefref = &xref; // can NOT say &&x;
    xrefref.get().set(4);

    return xref;
    }

Int count(List<String> list)
    {
    Int c = 0;
    Person p = ...;
    c++;
//    Ref<Int> cref = &c;

    function Void(String) visitor = s -> console.out(c);
    function Void(String) visitor = s -> p = new Person(s);
//    function Void(String) visitor = s -> cref.inc();

    list.visit(visitor);

    return c;
    }

// type narrowing on ref test

Object o = ...
if (o instanceof String && o.length > 4)
    {
    Ref<Object> oref = &o;  // Schrödinger's cat! opening the box (just getting the handle) ruins the type assumability
    print oref.RefType;     // prints "Object", not "String", although both compiler and runtime
                            // assume that it is (safely) a String (as long as there is no Ref!)
    oref.set(4);
    Char ch = o.charAt(4);  // can't do this now!!!
    // ...
    } // this line "detaches" oref from o (oref gets its own storage, and holds whatever o is
      // at this point

// Ref taking on (mixing in) capabilities of RefType

Int c = 0;
++c;    // what does this mean? what does it "compile as"

// option 1
c = c + 1;
INVOKE_11 c Int.add 1 -> c
// option 2
ADD c, 1 -> c
// option 3
INC c

Ref<Int> cref = &c;
cref.inc();

//

Object o;
Ref<Object> ref = &o;
o = new Person("bob");

//

class Person
    {
    construct Person(String name)
        {
        this.name = name;       // i.e. "this:struct[Person.name] = name;"
        }

    String name;
    }

    Person p = new Person("bob")

//

foo()
    {
    Frame frame = this:frame;
    print "cs:ip=%n:%n", frame.cs, frame.ip
    print "cs:ip=%n:%n", frame.cs, frame.ip
    }

// constructor usage dynamically to create a new class instance

Person p = new Person(name);
function Person(String) new1 = &(Person.new<Person>(String));

function Void() constructor = &(Person.construct(name));

Person p2 = Person.new(constructor);

//

class B { private Int x; Int y; }
class D extends B { Int x; }

construct D(Int x1, Int x2, Int y)
    {

    }

// class / singleton

interface /* TODO or class or const */ Module
    {
    Class resolveName(String name);
    }

const Class<ClassType>
    {
    conditional ClassType singleton();
    ClassType:struct newStruct();
    ClassType new(Tuple params = ());
    ClassType new(ClassType:struct struct);
    }

Class clzE = this:module.resolveName("dto.Employee")
Class clzC = this:module.resolveName("util.Config")
Class<Runnable> clzR = (<-) this:module.resolveName ("jobs.Reporter")

// -- meta

// auto-narrowing
class C
    {
    C foo();        // "C" will auto-narrow, i.e. this:type
    C.Type foo2();  // non-auto-narrowing "C"
    }

C o =  new C();
Ref<C> r = &o;

// how to get the "type" of o
Type t = o.Type;            // ugh. now Object has a Type property
Type t = &o.ActualType;     // yeah, this will work.
Type t = typeof(o);         // C called and wants its compiler back
Type t = o.meta.Type;       // only works if you have access to meta! (but it does make some sense)

// is o immutable? which one(s) of these would work?
if (o.immutable) ...        // ugh. now Object has an immutable property
if (&o.immutable) ...       // asking the reference seems weird
if (o.meta.immutable) ...   // obviously this could work, unless you can't get o.meta
if (o is immutable) ...     // hack

// ----

async Reader
    implements ...
    {
    }

// lazy prop

Point(Int x, Int y)
    {
    // lambda style
    @lazy(() -> x ^ y) Int hash;

    // alternatively use method bound to "this"
    @lazy(calcHash) Int hash;

    Int calcHash()
        {
        return x ^ y;
        }

    // or "implement the prop by over-riding calc"
    @lazy Int hash.calc()
        {
        return x ^ y;
        }
    }


// weak / soft prop
class C
    {

    }
const Movie
    {
    // ...
    @lazy(decompress) @soft Byte[] bytes;

    private Byte[] decompress() {...}
    }


class WHM<K,V>
    {
// got rid of this:    private RefStream notifier = new RefStream();
    class Entry<K,V>(K key, V value)
        {
        @weak(cleanup) K key;
        V value;
        // etc.

        Void cleanup()
            {
            WHM.this.remove(this);
            }
        }

    V get(K k)
        {
        [] a = getBucketArray()
        I  h = k.hash;
        E? e = a[h%a.length]
        while (e != null)
            {
            conditional K ko = e.&k.peek();
            if (ko)
               {
               (_, K k) = ko;

            if (K k2 : e.&k.peek() && k == k2)
                {
                return e.v;
                }
            }
        return null; // bad design; get() should be conditional
        }

    .. put(K k, V v)
        {
        Entry e = new Entry(k, v, notifier); // &key.tellMeAfterYouGCTheKey(notifier, this);

        // ...
        }

    Entry?[] getBucketArray()
        {
        // not this anymore: notifier.forEach(cleanup);
        this:service.processClearedRefEvents();
        // ...
        }


    }

// lambda

function Int(Int) foo(Int i)
    {
    return n -> n + i*i;

    const function Int(Int i, Int n) foo$1 = {return n + i*i;};
    return &foo$1(i,?);
    }

// auto-mixin (under consideration)

// what i want to be able to say is:
//   "yeah, there's a handy container data type C, and there's a handy data type D ...
//   but there's a third type that is implicit, which is a C<D>, and any time that
//   someone creates a C<D>, I have some extra capabilities that need to be included"
@auto mixin X           // name is required, and should be meaningful, although somewhat extraneous
        into C<D>
    {
    // the "this" is a C<D>
    }


// conditional type composition

class HandyDBDriver
        implements SpringResource // should only implement this if Spring is present
    {
    Connection connect(String url);

    // TODO SpringResource method(s)
    }

// mixins for refs

@lazy(function RefType ()?)
@weak(function Void ()?)
@soft(function Void ()?)
@future
@watch(function Void(RefType))

combos that work:
@lazy @weak
@lazy @soft

combos that don't work:
@lazy @future

TODO
@ro - HOW? WHAT? WTF?
@atomic
@inject

@auto

//  timeout

service Pi
    {
    String calc(Int digits)
        {
        String value;
        // some calculation code goes here
        // ...
        return value;
        }
    }

Void printPi(Console console)
    {
    Pi pi = new Pi();

    // blocking call to the Pi calculation service - wait for 100 digits
    console.print(pi.calc(100));

    // potentially async call to the Pi calculation service
    @future String fs = pi.withTimeout(...).calc(99999);
    fs.onResult(value -> console.print(value));
    fs.onThrown(e -> console.print(e.to<String>()));
    fs.onExpiry(() -> console.print("it took too long!"));
    fs.onFinish(() -> console.print("done"));
    }

Void foo()
    {
    this:service.pushSLA(5 seconds)
    printPi(console);
    this:service.popSLA()
    }


// --- atomic

service Progress
    {
    @watch(raiseEvents) Int percentDone;

    Void registerForUpdate(function Void (Int) notify) {list.add(notify);}

    private Void raiseEvents()
        {
        Int percent = percentDone;

        // notify each listener
        for (function Void (Int) notify : list)
            {
            notify(percent);
            }
        }
    }

service LongRunning
    {
    // instead of Progress
    public/private @atomic Int percentDone;

    Void runForALongTimeDoingSomethingImportant(Progress progress)
        {
        Int lastPercent = 0;
        for (Int i : 0..workCount)
            {
            Int percent = i * 100 / workCount
            // if (percent != lastPercent)
            if (percent != percentDone)
                {
                // progress.percentDone = percent;
                // lastPercent = percent;
                percentDone = percent;
                }

            // ...
        //
        }
    }

// lambda again

class c
    method m
        {
        foo(s -> s.length); // v1
        bar(y -> y.length); // v2
        }

// cas

String oldValue = ...
String newValue = ...
while (oldValue : casFailed(oldValue, newValue)
    {
    }

service LongRunning
    {
    @watch @atomic Int percentDone;
    // ....
    }

// future

// pairing down java's stage ..
public interface Future<T>
    {
    public <U> CompletionStage<U> thenApply(Function<? super T,? extends U> fn);

    public CompletionStage<Void> thenAccept(Consumer<? super T> action);

    public CompletionStage<Void> thenRun(Runnable action);

    public <U,V> CompletionStage<V> thenCombine
        (CompletionStage<? extends U> other,
         BiFunction<? super T,? super U,? extends V> fn);

    public <U> CompletionStage<Void> thenAcceptBoth
        (CompletionStage<? extends U> other,
         BiConsumer<? super T, ? super U> action);

    public CompletionStage<Void> runAfterBoth(CompletionStage<?> other,
                                              Runnable action);

    public <U> CompletionStage<U> applyToEither
        (CompletionStage<? extends T> other,
         Function<? super T, U> fn);

    public CompletionStage<Void> acceptEither
        (CompletionStage<? extends T> other,
         Consumer<? super T> action);

    public CompletionStage<Void> runAfterEither(CompletionStage<?> other,
                                                Runnable action);

    public <U> CompletionStage<U> thenCompose
        (Function<? super T, ? extends CompletionStage<U>> fn);

    public CompletionStage<T> exceptionally
        (Function<Throwable, ? extends T> fn);

    public CompletionStage<T> whenComplete
        (BiConsumer<? super T, ? super Throwable> action);

    public <U> CompletionStage<U> handle
        (BiFunction<? super T, Throwable, ? extends U> fn);
    }



service s

s.doSomething().thenDo(() -> ...).thenDo(() -> ...).thenDo(() -> ...).thenDo(() -> ...)

s.doSomething().or(s2.doSomething()).thenDo(() -> ...);
s.doSomething().and(s2.doSomething()).thenDo(() -> ...);

s.makeString().transform(s -> new IntLiteral(s).to<Int>()).passTo(countSlowly);
