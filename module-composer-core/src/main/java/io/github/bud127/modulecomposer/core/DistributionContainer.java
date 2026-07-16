package io.github.bud127.modulecomposer.core;

public record DistributionContainer(
        String image,
        String baseImage,
        Integer hostPort,
        Integer containerPort
) {
}
