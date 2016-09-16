package dodola.anole;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import dodola.anole.lib.Anole;

public class MainActivity extends AppCompatActivity {

    private TextView mDooooo;
    private final Hello hello = new Hello();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mDooooo = (TextView) findViewById(R.id.dooooooo);
        mDooooo.setText(hello.sayHello());
        this.findViewById(R.id.btn_patch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String pathFromAssets = Anole.getPathFromAssets(MainActivity.this, "patch.jar");

                Anole.applyPatch(MainActivity.this, pathFromAssets);

                mDooooo.setText(hello.sayHello());
            }
        });
    }
}
