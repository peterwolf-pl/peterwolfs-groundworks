package com.piotrek.groundworks.api.container;

/**
 * Optional capability for a world granular container that can reposition itself
 * in response to machine-to-machine workflow signals.
 *
 * <p>The contract intentionally stays generic: callers can ask the nearest
 * compatible mobile receiver to advance without depending on its concrete mod
 * or entity class.</p>
 */
public interface IMobileWorldGranularContainer extends IWorldGranularContainer {

    /**
     * Request a controlled forward movement.
     *
     * @param blocks requested travel distance in world blocks
     * @return true when the receiver accepted the movement request
     */
    boolean requestAdvance(double blocks);
}
