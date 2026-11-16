import java.util.*;

/** Small, dependency-free numerical expression compiler. No eval or script engine. */
public final class MathExpression {
    public interface Node { double eval(Map<String,Double> values); }
    private final String s;
    private int at;
    private MathExpression(String s) { this.s = s; }
    public static Node compile(String s) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException("数式が空です");
        // Keep the original π as an identifier through a lexical replacement.
        MathExpression p = new MathExpression(s.replace("π", "pi"));
        Node value = p.add(); p.ws();
        if (p.at != p.s.length()) throw p.bad("余分な文字");
        return value;
    }
    private IllegalArgumentException bad(String message) { return new IllegalArgumentException(message + " (位置 " + (at+1) + "): " + s); }
    private void ws() { while (at < s.length() && Character.isWhitespace(s.charAt(at))) at++; }
    private boolean take(char c) { ws(); if (at < s.length() && s.charAt(at) == c) { at++; return true; } return false; }
    private char peek() { ws(); return at < s.length() ? s.charAt(at) : '\0'; }
    private Node add() {
        Node result=mul();
        while (true) {
            if(take('+')) { Node a=result,b=mul(); result=v->a.eval(v)+b.eval(v); }
            else if(take('-')) { Node a=result,b=mul(); result=v->a.eval(v)-b.eval(v); }
            else return result;
        }
    }
    private Node mul() {
        Node result=unary();
        while(true) {
            if(take('*')) { Node a=result,b=unary(); result=v->a.eval(v)*b.eval(v); }
            else if(take('/')) { Node a=result,b=unary(); result=v->a.eval(v)/b.eval(v); }
            else if(take('%')) { Node a=result,b=unary(); result=v->a.eval(v)%b.eval(v); }
            else {
                char c=peek();
                if (c=='(' || Character.isDigit(c) || Character.isLetter(c) || c=='_') {
                    Node a=result,b=unary(); result=v->a.eval(v)*b.eval(v);
                } else return result;
            }
        }
    }
    private Node unary() {
        if(take('+')) return unary();
        if(take('-')) { Node a=unary(); return v->-a.eval(v); }
        return pow();
    }
    private Node pow() {
        Node a=primary();
        if(take('^')) { Node b=unary(); return v->Math.pow(a.eval(v),b.eval(v)); }
        return a;
    }
    private Node primary() {
        if(take('(')) {
            Node a=add();
            if (!take(')')) throw bad("閉じ括弧がありません");
            return a;
        }
        char c=peek();
        if (Character.isDigit(c) || c=='.') {
            int start=at; boolean hasDigits=false;
            while(at<s.length() && Character.isDigit(s.charAt(at))) { at++; hasDigits=true; }
            if(at<s.length() && s.charAt(at)=='.') {
                at++;
                while(at<s.length() && Character.isDigit(s.charAt(at))) { at++; hasDigits=true; }
            }
            if (!hasDigits) throw bad("数値が正しくありません");
            if(at<s.length() && (s.charAt(at)=='e' || s.charAt(at)=='E')) {
                int old=at; at++;
                if(at<s.length() && (s.charAt(at)=='+' || s.charAt(at)=='-')) at++;
                int exp=at;
                while(at<s.length() && Character.isDigit(s.charAt(at))) at++;
                if (exp==at) at=old;
            }
            final double n;
            try { n=Double.parseDouble(s.substring(start,at)); }
            catch(NumberFormatException ex) { throw bad("数値が大きすぎます"); }
            return v->n;
        }
        if (Character.isLetter(c) || c=='_') {
            int start=at;
            while(at<s.length() && (Character.isLetterOrDigit(s.charAt(at)) || s.charAt(at)=='_')) at++;
            String name=s.substring(start,at).toLowerCase(Locale.ROOT);
            if (peek()=='(' && isFunction(name)) {
                take('('); ArrayList<Node> args=new ArrayList<>();
                if(!take(')')) {
                    do { args.add(add()); } while(take(','));
                    if(!take(')')) throw bad("関数の閉じ括弧がありません");
                }
                int arity = (name.equals("min") || name.equals("max") || name.equals("pow") || name.equals("atan2") || name.equals("hypot") || name.equals("mod"))?2:1;
                if(args.size()!=arity) throw bad(name + " の引数は"+arity+"個必要です");
                return v->function(name,args,v);
            }
            if(name.equals("pi")) return v->Math.PI;
            if(name.equals("e")) return v->Math.E;
            return v->{
                Double val=v.get(name);
                if(val==null) throw new IllegalArgumentException("未定義の変数: " + name);
                return val;
            };
        }
        throw bad("式を解釈できません");
    }
    private static boolean isFunction(String name) {
        return Set.of("sin","cos","tan","asin","acos","atan","sinh","cosh","tanh","sqrt","abs","ln","log","log10","exp","floor","ceil","round","sign","cbrt","min","max","pow","atan2","hypot","mod").contains(name);
    }
    private static double function(String n,List<Node> a,Map<String,Double> v) {
        double x=a.get(0).eval(v);
        return switch(n) {
            case "sin" -> Math.sin(x); case "cos" -> Math.cos(x); case "tan" -> Math.tan(x);
            case "asin" -> Math.asin(x); case "acos" -> Math.acos(x); case "atan" -> Math.atan(x);
            case "sinh" -> Math.sinh(x); case "cosh" -> Math.cosh(x); case "tanh" -> Math.tanh(x);
            case "sqrt" -> Math.sqrt(x); case "abs" -> Math.abs(x); case "ln" -> Math.log(x);
            case "log","log10" -> Math.log10(x); case "exp" -> Math.exp(x); case "floor" -> Math.floor(x);
            case "ceil" -> Math.ceil(x); case "round" -> Math.floor(x+0.5); case "sign" -> Math.signum(x);
            case "cbrt" -> Math.cbrt(x); case "min" -> Math.min(x,a.get(1).eval(v));
            case "max" -> Math.max(x,a.get(1).eval(v)); case "pow" -> Math.pow(x,a.get(1).eval(v));
            case "atan2" -> Math.atan2(x,a.get(1).eval(v)); case "hypot" -> Math.hypot(x,a.get(1).eval(v));
            case "mod" -> x%a.get(1).eval(v);
            default -> Double.NaN;
        };
    }
}
