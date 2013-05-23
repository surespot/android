package com.twofours.surespot;

/**
 * Container to ease passing around a tuple of two objects. This object provides a sensible
 * implementation of equals(), returning true if equals() is true on each of the contained
 * objects.
 */
public class Tuple<F, S> {
    public final F first;
    public final S second;
    /**
     * Constructor for a Pair.
     *
     * @param first the first object in the Pair
     * @param second the second object in the pair
     */
    public Tuple(F first, S second) {
        this.first = first;
        this.second = second;
        
    }

  

    /**
     * Convenience method for creating an appropriately typed pair.
     * @param a the first object in the Pair
     * @param b the second object in the pair
     * @return a Pair that is templatized with the types of a and b
     */
    public static <A, B> Tuple <A, B> create(A a, B b) {
        return new Tuple<A, B>(a, b);
    }
}
