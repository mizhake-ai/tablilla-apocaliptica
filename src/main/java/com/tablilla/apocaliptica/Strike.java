package com.tablilla.apocaliptica;

/** Un ataque en curso. Se llama una vez por tick del servidor. */
public interface Strike {
    /** Se llama una vez al empezar (aqui se avisa a los clientes para los efectos visuales). */
    default void start() {}

    /** Se llama si el ataque se cancela con /tablilla stop. */
    default void cancel() {}

    /** @return true cuando el ataque ya termino. */
    boolean tick();
}
