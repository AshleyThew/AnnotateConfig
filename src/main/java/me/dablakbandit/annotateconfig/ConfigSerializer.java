package me.dablakbandit.annotateconfig;

public interface ConfigSerializer<T> {
    Object serialize(T value);

    T deserialize(Object raw);
}
