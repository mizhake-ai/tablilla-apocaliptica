package com.tablilla.apocaliptica;

/** Un ataque en curso. Se llama una vez por tick del servidor. */
public interface Strike {
    /** @return true cuando el ataque ya termino. */
    boolean tick();
}
