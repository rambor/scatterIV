package version4;

/**
 * Created by robertrambo on 08/02/2016.
 */
public class PDBAtom {

    private double xpos;
    private double ypos;
    private double zpos;
    private double occ;
    private double temp;

    private int mappedResID;

    private int atomIndex;
    private int atomicNumber;
    private int resid;

    private String chainID;
    private String atom;
    private String alt;

    private String residue; // 3-letter code for protein
    private String atomType;
    private double[] coords;
    private boolean isWater=false;

    // copy constructor
    public PDBAtom(PDBAtom atom){

        this.atomIndex =atom.atomIndex;
        this.xpos =atom.xpos;
        this.ypos =atom.ypos;
        this.zpos =atom.zpos;
        this.occ = atom.occ;
        this.temp = atom.temp;
        this.resid = atom.resid;
        this.chainID = atom.chainID;
        this.atom = atom.atom;
        this.alt = atom.alt;
        this.residue = atom.residue;
        this.atomType = atom.atomType;
        this.atomicNumber = atom.atomicNumber;

        double[] temp = atom.getCoords();
        this.coords = new double[]{temp[0], temp[1], temp[2]};
        this.isWater = atom.isWater;
    }

    //private String residueType; //DNA, RNA, protein, CARB
    public PDBAtom(String line){
        this.atomIndex = Integer.parseInt(line.substring(6,11).trim());
        this.atomType = line.substring(12,16).trim();
        // System.out.println(line);
        // convert to atomic number
        this.convertToAtomicNumber();

        String tempResidue = line.substring(17,20).trim();
        // check residue, convert to 3 letter code

        this.alt = line.substring(16,17).trim();
        this.chainID = line.substring(21,22).trim();

        this.resid = Integer.parseInt(line.substring(22,26).trim());

        this.xpos = Double.parseDouble(line.substring(30,38).trim());
        this.ypos = Double.parseDouble(line.substring(38,46).trim());
        this.zpos = Double.parseDouble(line.substring(46,54).trim());

        coords = new double[]{xpos, ypos, zpos};

        int stringLength = line.length();

        if (stringLength >= 60){
            this.occ = Double.parseDouble(line.substring(54,60).trim());
        }

        if (stringLength >= 66){
            this.temp = Double.parseDouble(line.substring(60,66).trim());
        }

        if (stringLength >= 78){
            this.atom = line.substring(76,78).trim();
        }

        this.residue = tempResidue;

        if (this.residue.equals("HOH")){
            isWater = true;
        }
    }

    public double[] getCoords(){
        return coords;
    }

    public void setCoords(double[] values){
        coords[0]=values[0];
        coords[1]=values[1];
        coords[2]=values[2];
        xpos = values[0];
        ypos = values[1];
        zpos = values[2];
    }

    public double getOccupancy (){
        return occ;
    }

    public String getAtomType(){
        return atomType;
    }

    public boolean isWater(){
        return isWater;
    }

    public String getPDBline(){
        if (this.isWater()){
            return String.format("%-6s%5d %4s%1s%3s %1s%4d%1s   %8.3f%8.3f%8.3f%6.2f%6.2f %n", "HETATM", atomIndex, atomType, " ", residue, chainID, resid, " ", xpos, ypos, zpos, occ, temp);
        } else {
            return String.format("%-6s%5d %4s%1s%3s %1s%4d%1s   %8.3f%8.3f%8.3f%6.2f%6.2f %n", "ATOM  ", atomIndex, atomType, " ", residue, chainID, resid, " ", xpos, ypos, zpos, occ, temp);
        }
    }

    private void convertToAtomicNumber(){

        char[] atomTypes = atomType.toCharArray();

        if (atomTypes[0] == 'C') {
            atomicNumber = 6;
        } else if (atomTypes[0] == 'N') {
            atomicNumber = 7;
        } else if (atomTypes[0] == 'O') {
            atomicNumber = 8;
        } else if (atomTypes[0] == 'P' ) {
            atomicNumber = 15;
        } else if (atomTypes[0] == 'S' ) {
            atomicNumber = 16;
        } else if (atomTypes[0] == 'H' ) {
            atomicNumber = 1;
        } else if (atomTypes.length > 1 && atomTypes[1] == 'H' ) {
            atomicNumber = 1;
        } else if (atomTypes[0] == 'H' && (atomTypes[1] == 'O') ) {
            atomicNumber = 1;
        } else if ((atomTypes[0] == 'H') && (atomTypes[2] == 'O') ) {
            atomicNumber = 1;
        } else if ((atomTypes[0] == 'H') && (atomTypes[1] == 'E')  ) {
            atomicNumber = 2;
        } else if ((atomTypes[0] == 'L') && (atomTypes[1] == 'I') ) {
            atomicNumber = 3;
        } else if ((atomTypes[0] == 'B') && (atomTypes[1] == 'E')  ) {
            atomicNumber = 4;
        } else if ((atomTypes[1] == 'B') ) {
            atomicNumber = 5;
        } else if ((atomTypes[1] == 'F') ) {
            atomicNumber = 9;
        } else if ((atomTypes[0] == 'N') && (atomTypes[1] == 'E') ) {
            atomicNumber = 10;
        } else if ((atomTypes[0] == 'N') && (atomTypes[1] == 'A')  ) {
            atomicNumber = 11;
        } else if ((atomTypes[0] == 'M') && (atomTypes[1] == 'G')  ) { //Mg
            atomicNumber = 12;
        } else if ((atomTypes[0] == 'A') && (atomTypes[1] == 'L')  ) {
            atomicNumber = 13;
        } else if ((atomTypes[0] == 'S') && (atomTypes[1] == 'I')  ) {
            atomicNumber = 14;
        } else if ((atomTypes[0] == 'C') && (atomTypes[1] == 'L')  ) {
            atomicNumber = 17;
        } else if ((atomTypes[0] == 'A') && (atomTypes[1] == 'R') ) {
            atomicNumber = 18;
        } else if ((atomTypes[1] == 'K')  ) {	// K
            atomicNumber = 19;
        } else if ((atomTypes[0] == 'C') && (atomTypes[1] == 'A')  ) { // Ca
            atomicNumber = 20;
        } else if ((atomTypes[0] == 'M') && (atomTypes[1] == 'N')  ) { // Mn
            atomicNumber = 25;
        } else if ((atomTypes[0] == 'F') && (atomTypes[1] == 'E')  ) { // Fe
            atomicNumber = 26;
        } else if ((atomTypes[0] == 'C') && (atomTypes[1] == 'O')  ) { // Co
            atomicNumber = 27;
        } else if ((atomTypes[0] == 'N') && (atomTypes[1] == 'I')  ) { // Ni
            atomicNumber = 28;
        } else if ((atomTypes[0] == 'C') && (atomTypes[1] == 'U')  ) { // Cu
            atomicNumber = 29;
        } else if ((atomTypes[0] == 'Z') && (atomTypes[1] == 'N')  ) { // Zn
            atomicNumber = 30;
        } else if ((atomTypes[0] == 'S') && (atomTypes[1] == 'E')  ) { // SE
            atomicNumber = 34;
        } else if ((atomTypes[0] == 'O') ) { //HOH water
            atomicNumber = 99;
        } else {

            //cout << "DEBUG: Couldn't find atomic number for atomType : " << atomType << endl;

        }
    }

    public int getAtomicNumber(){
        return atomicNumber;
    }
}