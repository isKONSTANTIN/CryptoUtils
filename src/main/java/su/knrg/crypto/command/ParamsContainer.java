package su.knrg.crypto.command;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;

public class ParamsContainer {
    protected List<String> rawParams;

    public ParamsContainer(List<String> rawParams) {
        this.rawParams = rawParams;
    }

    public ParamsContainer(String ... rawParams) {
        this.rawParams = List.of(rawParams);
    }

    protected Optional<String> rawValue(int index) {
        return Optional.ofNullable(rawParams.size() > index ? rawParams.get(index) : null);
    }

    protected <T> Optional<T> mappedValue(int index, Function<? super String, T> mapper) {
        try {
            return rawValue(index).map(mapper);
        }catch (Exception e) {
            return Optional.empty();
        }
    }

    public int size() {
        return rawParams.size();
    }

    public Optional<String> stringV(int index) {
        return rawValue(index);
    }

    public Optional<Integer> intV(int index) {
        return mappedValue(index, Integer::parseInt);
    }

    public Optional<Boolean> booleanV(int index) {
        return mappedValue(index, Boolean::parseBoolean);
    }

    public Optional<Float> floatV(int index) {
        return mappedValue(index, Float::parseFloat);
    }

    public Optional<Double> doubleV(int index) {
        return mappedValue(index, Double::parseDouble);
    }

    public Optional<UUID> uuidV(int index) {
        return mappedValue(index, UUID::fromString);
    }

    public Optional<BigDecimal> bigDecimalV(int index) {
        return mappedValue(index, BigDecimal::new);
    }
}