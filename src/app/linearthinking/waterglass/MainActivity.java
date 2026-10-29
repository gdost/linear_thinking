package app.linearthinking.waterglass;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Random;

public class MainActivity extends Activity implements GameView.Listener {

    private enum State { LINEAR_ASK, LINEAR_ANIM, LINEAR_DONE, DOUBLE_ASK, DOUBLE_ANIM, DOUBLE_DONE }

    private final Random random = new Random();
    private GameView game;
    private TextView question;
    private TextView result;
    private Button button;

    private State state;
    private int n;
    private double base;
    private double guess;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(0xFFF7FBFF);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        int pad = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF7FBFF);

        question = new TextView(this);
        question.setTextColor(0xFF1C2A40);
        question.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        question.setPadding(pad, pad, pad, dp(4));
        root.addView(question, new LinearLayout.LayoutParams(-1, -2));

        game = new GameView(this);
        game.setListener(this);
        root.addView(game, new LinearLayout.LayoutParams(-1, 0, 1f));

        result = new TextView(this);
        result.setTextColor(0xFF1C2A40);
        result.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        result.setPadding(pad, dp(8), pad, dp(4));
        root.addView(result, new LinearLayout.LayoutParams(-1, -2));

        button = new Button(this);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(0xFF3D8FE0);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(56));
        bp.setMargins(pad, dp(4), pad, pad);
        root.addView(button, bp);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (state == State.LINEAR_DONE) startDoubling();
                else if (state == State.DOUBLE_DONE) newGame();
            }
        });

        setContentView(root);
        newGame();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void newGame() {
        n = 14 + random.nextInt(7); // 14..20
        double lo = Math.max(5, 0.40 * GameView.CAP / n);
        double hi = Math.min(25, 0.92 * GameView.CAP / n);
        base = Math.round(lo + random.nextDouble() * (hi - lo));
        state = State.LINEAR_ASK;
        game.reset(base);
        question.setText("The glass holds " + Fmt.ml(base) + " of water.\n"
                + "Where would " + n + " × this amount reach? Tap the height.");
        result.setText("");
        button.setVisibility(View.INVISIBLE);
    }

    private void startDoubling() {
        state = State.DOUBLE_ASK;
        game.reset(base);
        question.setText("Again " + Fmt.ml(base) + ". Now double it " + n + " times:\n"
                + "where would " + Fmt.pow2(n) + " × this amount reach? Tap the height.");
        result.setText("");
        button.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onGuess(double ml) {
        guess = ml;
        if (state == State.LINEAR_ASK) {
            state = State.LINEAR_ANIM;
            result.setText("Your guess: " + Fmt.ml(ml) + ". Let's pour…");
            game.animateLinear(n * base);
        } else if (state == State.DOUBLE_ASK) {
            state = State.DOUBLE_ANIM;
            result.setText("Your guess: " + Fmt.ml(ml) + ". Let's start doubling…");
            game.animateDoubling(n);
        }
    }

    @Override
    public void onAnimationDone() {
        if (state == State.LINEAR_ANIM) {
            state = State.LINEAR_DONE;
            double correct = n * base;
            double off = Math.abs(guess - correct);
            result.setText("Your guess: " + Fmt.ml(guess) + "\n"
                    + "Correct: " + n + " × " + Fmt.ml(base) + " = " + Fmt.ml(correct) + "\n"
                    + "Off by " + Fmt.ml(off) + " (" + Math.round(100 * off / GameView.CAP)
                    + "% of the glass). Linear growth is easy to picture.");
            button.setText("Next question");
            button.setVisibility(View.VISIBLE);
        } else if (state == State.DOUBLE_ANIM) {
            state = State.DOUBLE_DONE;
            double correct = base * Math.pow(2, n);
            double ratio = correct / guess;
            result.setText("Your guess: " + Fmt.ml(guess) + " (" + Fmt.factor(guess / base) + " × the start)\n"
                    + "Correct: " + Fmt.pow2(n) + " × " + Fmt.ml(base) + " = " + Fmt.volume(correct)
                    + " = " + Fmt.glasses(correct / GameView.CAP) + "\n"
                    + "Your guess was " + Fmt.factor(ratio) + " times too small. "
                    + "We think in straight lines, not in doublings.");
            button.setText("Play again");
            button.setVisibility(View.VISIBLE);
        }
    }
}
