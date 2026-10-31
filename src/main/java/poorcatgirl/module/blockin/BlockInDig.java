package poorcatgirl.module.blockin;

public final class BlockInDig {
    public enum Action { START, PROGRESS, STOP, WAIT, DONE, ABORT, REJECTED }
    private boolean started, stopped, terminal;
    private int waitTicks;
    private float progress;
    private Action result;

    public Action step(boolean visible,boolean sameState,boolean serverRemoved,float delta) {
        if (terminal) return result;
        if (serverRemoved) return finish(Action.DONE);
        if (!sameState) return finish(Action.ABORT);
        if (stopped) return ++waitTicks>=40 ? finish(Action.REJECTED) : Action.WAIT;
        if (!visible || !Float.isFinite(delta) || delta<=0) return finish(Action.ABORT);
        progress=Math.min(1,progress+delta);
        if (!started) {started=true;return Action.START;}
        if (progress>=1) {stopped=true;return Action.STOP;}
        return Action.PROGRESS;
    }
    private Action finish(Action action) {terminal=true;result=action;return action;}
    public boolean cancel() {
        boolean abort=started && !terminal;finish(Action.ABORT);return abort;
    }
    public float progress() {return progress;}
    public boolean waiting() {return stopped && !terminal;}
}
