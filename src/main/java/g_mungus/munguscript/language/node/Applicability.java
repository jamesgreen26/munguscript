package g_mungus.munguscript.language.node;

/**
 * Which targets a node is meant for, in the host's terms. The engine never looks inside; it only
 * hands this back to the host to match against the target a run is aimed at. A Minecraft host
 * would use block ids and block tags.
 */
public interface Applicability {
}
