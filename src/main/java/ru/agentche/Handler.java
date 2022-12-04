package ru.agentche;

import java.io.BufferedOutputStream;

/**
 * @author Aleksey Anikeev aka AgentChe
 * Date of creation: 04.12.2022
 */
@FunctionalInterface
public interface Handler {
    void handle(Request request, BufferedOutputStream responseStream);
}