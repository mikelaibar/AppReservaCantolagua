package es.cantolagua.misemana;

import android.app.*;
import android.os.*;
import java.time.*;
import java.util.*;

/** Only for a disposable emulator: creates synthetic state before installing an update. */
public final class UpgradeSeed extends Instrumentation {
  public void onCreate(Bundle args) {
    super.onCreate(args);
    start();
  }

  public void onStart() {
    Bundle result = new Bundle();
    try {
      Store store = new Store(getTargetContext());
      store.erase();
      store.credentials("upgrade-test", "synthetic-upgrade-password");
      store.slots(
          Plan.monday(LocalDate.now(Plan.ZONE)),
          Collections.singletonList(new Plan.Slot(1, "14", "Step-Training", "09:20")),
          true);
      result.putString(
          "stream",
          "UPGRADE_SEED_READY: synthetic credentials and weekly selection saved; automation"
              + " paused.\n");
      finish(Activity.RESULT_OK, result);
    } catch (Exception e) {
      result.putString("stream", "FAIL " + android.util.Log.getStackTraceString(e));
      finish(Activity.RESULT_CANCELED, result);
    }
  }
}
