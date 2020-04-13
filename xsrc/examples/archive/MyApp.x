/**
 * This is an entire module in a file.
 */
module MyApp
    {
    package ecstasy import Ecstasy.xtclang.org v:"1.2.3";
//          avoid v:"1.0.3"
//          allow v:"1.2.1.rc2", v:"1.1.beta7"
//          prefer v:"1.2.1", v:"1.1"
//          avoid v:"2.0";

    package hibernate import:optional Hibernate.redhat.com;

    package util
        {
        /**
         * So much happiness.
         */
        enum Happiness
                if (Hibernate.present)
                    {
                    implements HibSerializable
                    }
                if (Coherence.present)
                    {
                    implements ExternalizableLite
                    }
                implements Externalizable
            { SAD, MEDIOCRE, HAPPY, ECSTATIC }


        if (Hibernate.present)
            {
            class Bob implements Runnable {}
            }
        else
            {
            Void Bob(String s) {print(s);}
            }

        /**
         * So much light.
         */
        enum Light
            {
            /**
             * night time
             */
            DARK("turn on a light"),
            /**
             * sun is out
             */
            LIGHT("turn off a light");

            construct Light(String s)
                {
                // this.s = s;
                }
            String s = ╔═════════════════════╗
                       ║This could be any    ║
                       ║freeform text that   ║
                       ║could be inside of an║
                       ║Ecstasy source file  ║
                       ╚═════════════════════╝;
            String s2 = "This could be any    "
                      + "\nfreeform text that"
                      + "\ncould be inside of an"
                      + "\nEcstasy source file";

            Map<Int, String> map = Map:{0="zero", 1="one", 2="two"};
            List<String> list = {"hello", "world", "!"};
            //List<Dec> list = {1.0, 2.0, 3.0};

            Binary b1 = Binary:{"abcdef"};
            Binary b2 = Binary:{
                ╔═════════════════════╗
                ║ 9149AF2aCF75b3B8E123║
                ║ 0f9149AcF2CF73BE123 ║
                ╚═════════════════════╝};
            Binary b3 = Binary:{
                 9149AF2aCF75b3B8E123
                 a0f9149AcF2CF73BE123
                };

            Int result1 = a();
            Int result2 = a(1);
            Int result3 = a("test", "args");
            Int result = a.b.c().d.e(1).f("test", "args");
            }
        class SuperMap<Key, Value>
            implements Map<Key, Value>
            {
            Int size.get()
                {
                return 0;
                }

            private <SomeType> List<SomeType> doSomethingWith(SomeType value);
            }
        }

    package forms
        {
        class MainWindow
            {
            Boolean visible.get()
                {
                return true;
                }

            Void move(Int dx, Int dy)
                {
                assert dx >= 0;
                assert:once dy >= 0;
                assert:test dx > 0 && dy >= 0;
                assert:debug;
                }

            Void paint()
                {
                // do something here
                }
            }
        }
    }